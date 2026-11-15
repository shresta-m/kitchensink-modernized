import type { FieldViolation, Problem } from '../types/api'

const BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const TOKEN_KEY = 'ks.token'

/** The token lives in sessionStorage: it dies with the tab and is never sent anywhere but the API. */
export const tokenStore = {
  get: (): string | null => {
    try {
      return sessionStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set: (token: string) => {
    try {
      sessionStorage.setItem(TOKEN_KEY, token)
    } catch {
      /* storage unavailable: the session simply will not survive a reload */
    }
  },
  clear: () => {
    try {
      sessionStorage.removeItem(TOKEN_KEY)
    } catch {
      /* ignore */
    }
  },
}

let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly errors: FieldViolation[]
  readonly traceId?: string

  constructor(status: number, message: string, code = 'UNKNOWN', errors: FieldViolation[] = [], traceId?: string) {
    super(message)
    this.status = status
    this.code = code
    this.errors = errors
    this.traceId = traceId
  }

  /** field -> message, for showing server-side validation next to the inputs. */
  fieldErrors(): Record<string, string> {
    return Object.fromEntries(this.errors.map((e) => [e.field, e.message]))
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  json?: unknown
  form?: FormData
  query?: Record<string, string | number | undefined>
  /** Login must not trigger the global "session expired" handling. */
  skipAuthHandling?: boolean
  signal?: AbortSignal
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const params = new URLSearchParams()
  Object.entries(query ?? {}).forEach(([k, v]) => {
    if (v !== undefined && v !== '') params.set(k, String(v))
  })
  const qs = params.toString()
  return `${BASE}/api/v1${path}${qs ? `?${qs}` : ''}`
}

async function send(path: string, opts: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json, */*' }
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`
  let body: BodyInit | undefined
  if (opts.json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(opts.json)
  } else if (opts.form) {
    body = opts.form // the browser sets the multipart boundary itself
  }

  let res: Response
  try {
    res = await fetch(buildUrl(path, opts.query), { method: opts.method ?? 'GET', headers, body, signal: opts.signal })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.', 'NETWORK_ERROR')
  }

  if (!res.ok) {
    if (res.status === 401 && !opts.skipAuthHandling) onUnauthorized()
    throw await toApiError(res)
  }
  return res
}

async function toApiError(res: Response): Promise<ApiError> {
  let problem: Partial<Problem> = {}
  try {
    problem = (await res.json()) as Problem
  } catch {
    /* non-JSON error body (proxy error page, etc.) */
  }
  const message = problem.detail || problem.title || `Request failed (${res.status})`
  return new ApiError(res.status, message, problem.code ?? 'UNKNOWN', problem.errors ?? [], problem.traceId)
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const res = await send(path, opts)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

/** Authenticated file download (the Authorization header rules out a plain <a href>). */
export async function download(path: string, fallbackName: string, query?: RequestOptions['query']): Promise<void> {
  const res = await send(path, { query })
  const disposition = res.headers.get('Content-Disposition') ?? ''
  const name = /filename="?([^";]+)"?/i.exec(disposition)?.[1] ?? fallbackName
  const url = URL.createObjectURL(await res.blob())
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
