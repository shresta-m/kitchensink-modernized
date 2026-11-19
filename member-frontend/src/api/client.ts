import type { ApiErrorBody, ErrorCode, FieldViolation } from './types';

// The access token lives in memory only (never localStorage/sessionStorage), so XSS cannot lift it from storage.
// Trade-off: a page reload signs the user out. Tokens last 30 minutes and there is no refresh token.
// Sign-out is also server side (POST /auth/logout bumps the member's token version), so a copied token dies with it.
let accessToken: string | null = null;

export const tokenStore = {
  get: (): string | null => accessToken,
  set: (token: string) => {
    accessToken = token;
  },
  clear: () => {
    accessToken = null;
  },
};

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: FieldViolation[];
  readonly code: ErrorCode | undefined;
  /** From the Retry-After header of a 429: how long a lockout lasts. */
  readonly retryAfterSeconds: number | undefined;

  constructor(status: number, message: string, fieldErrors: FieldViolation[] = [], code?: ErrorCode, retryAfterSeconds?: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

let onUnauthorized: (() => void) | null = null;

/** AuthContext registers a callback so a rejected token (expired, revoked, member deleted) ends the local session. */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

type Query = Record<string, string | number | undefined>;

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  query?: Query;
}

function buildUrl(path: string, query?: Query): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== '') params.set(key, String(value));
  }
  const qs = params.toString();
  return `/api/v1${path}${qs ? `?${qs}` : ''}`;
}

function randomHex(bytes: number): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(bytes)), (b) => b.toString(16).padStart(2, '0')).join('');
}

/** W3C trace context: the browser starts the trace, so one id links this call, the server logs and the MongoDB spans. */
function traceparent(): string {
  return `00-${randomHex(16)}-${randomHex(8)}-01`;
}

function headers(hasBody: boolean): Headers {
  const h = new Headers({ Accept: 'application/json', traceparent: traceparent() });
  if (hasBody) h.set('Content-Type', 'application/json');
  const token = tokenStore.get();
  if (token) h.set('Authorization', `Bearer ${token}`);
  return h;
}

async function toApiError(res: Response): Promise<ApiError> {
  let body: Partial<ApiErrorBody> = {};
  try {
    body = (await res.json()) as Partial<ApiErrorBody>;
  } catch {
    // Non-JSON error body: fall back to the status text below.
  }
  const retryAfter = Number(res.headers.get('Retry-After'));
  return new ApiError(
    res.status,
    body.message || res.statusText || 'Request failed',
    body.fieldErrors ?? [],
    body.code,
    Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined,
  );
}

/** A 401 while we hold a token means the session is over. A 401 on sign-in (wrong password) is just an error. */
function sessionRejected(res: Response) {
  if (res.status === 401 && tokenStore.get() !== null) onUnauthorized?.();
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T | undefined> {
  const res = await fetch(buildUrl(path, options.query), {
    method: options.method ?? 'GET',
    headers: headers(options.body !== undefined),
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  sessionRejected(res);
  if (!res.ok) throw await toApiError(res);
  if (res.status === 204) return undefined;
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : undefined;
}

export interface Download {
  blob: Blob;
  filename: string;
}

function filenameFrom(res: Response, fallback: string): string {
  const header = res.headers.get('Content-Disposition') ?? '';
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header);
  return match ? decodeURIComponent(match[1]) : fallback;
}

/** Binary download (CSV). Resolves to undefined when the body is empty. */
export async function requestBlob(path: string, query?: Query, fallbackName = 'download'): Promise<Download | undefined> {
  const res = await fetch(buildUrl(path, query), { headers: headers(false) });
  sessionRejected(res);
  if (!res.ok) throw await toApiError(res);
  const blob = await res.blob();
  return blob.size > 0 ? { blob, filename: filenameFrom(res, fallbackName) } : undefined;
}
