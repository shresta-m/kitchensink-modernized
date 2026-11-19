import { ApiError } from '../api/client';
import type { ErrorCode } from '../api/types';

export const EMPTY_RESPONSE_MESSAGE = 'The server answered with an empty response. Try again.';

const BY_CODE: Partial<Record<ErrorCode, string>> = {
  CONCURRENT_MODIFICATION: 'This member was changed by someone else. Close this and reload the list to get the latest version.',
  ADMIN_MUST_BE_DEMOTED: 'Administrators cannot be deleted directly. Change their role to Viewer first, then delete.',
  LAST_ADMIN: 'At least one administrator must remain. Promote another member first.',
  UNAUTHENTICATED: 'Your session has ended: it expired, or you signed out or changed your password elsewhere. Sign in again.',
  ADMIN_PASSWORD_RESET_FORBIDDEN: 'An administrator\'s password cannot be reset. Change their role to Viewer first, reset it, then promote them again.',
  ACCOUNT_PENDING: 'Your registration is waiting for administrator approval. You can sign in once it is approved.',
  NOT_PENDING_APPROVAL: 'This member is already approved. Reload the list.',
  ACCESS_DENIED: 'You do not have permission to do that.',
  PAGE_TOO_DEEP: 'That page is too far down the list. Narrow the search or filters instead.',
  EXPORT_TOO_LARGE: 'Too many rows to export. Narrow the search or filters and try again.',
};

/** "15 minutes", "1 minute", "40 seconds": a lockout length a person can act on. */
export function formatWait(seconds: number | undefined): string {
  if (seconds === undefined) return 'a few minutes';
  if (seconds < 60) return `${seconds} second${seconds === 1 ? '' : 's'}`;
  const minutes = Math.ceil(seconds / 60);
  return `${minutes} minute${minutes === 1 ? '' : 's'}`;
}

export function describeError(e: unknown): string {
  if (e instanceof ApiError && e.code === 'TOO_MANY_ATTEMPTS') {
    return `Too many attempts. Try again in ${formatWait(e.retryAfterSeconds)}.`;
  }
  if (e instanceof ApiError) return (e.code && BY_CODE[e.code]) || e.message;
  if (e instanceof TypeError) return 'Cannot reach the server. Is the member-service running on port 8080?';
  return 'Something went wrong. Try again.';
}
