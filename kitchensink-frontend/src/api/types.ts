export type Role = 'ADMIN' | 'VIEWER';
export type MemberStatus = 'ACTIVE' | 'TEMP_PASSWORD' | 'PENDING_APPROVAL';

export interface PageResponse<T> {
  content: T[];
  page: number; // zero-based
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface LookupOption<T extends string = string> {
  value: T;
  label: string;
}

export interface FieldViolation {
  field: string;
  message: string;
}

export type ErrorCode =
  | 'VALIDATION_FAILED' | 'MALFORMED_REQUEST' | 'INVALID_ADMIN_CODE' | 'INVALID_SORT' | 'EXPORT_TOO_LARGE'
  | 'MEMBER_NOT_FOUND' | 'DUPLICATE_EMAIL' | 'DUPLICATE_RESOURCE' | 'LAST_ADMIN' | 'ADMIN_MUST_BE_DEMOTED'
  | 'PAGE_TOO_DEEP' | 'CONCURRENT_MODIFICATION' | 'REQUEST_REJECTED' | 'INVALID_CREDENTIALS' | 'INVALID_PASSWORD'
  | 'UNAUTHENTICATED' | 'ACCESS_DENIED' | 'INTERNAL_ERROR' | 'TOO_MANY_ATTEMPTS' | 'ADMIN_PASSWORD_RESET_FORBIDDEN'
  | 'ACCOUNT_PENDING' | 'NOT_PENDING_APPROVAL';

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  code: ErrorCode;
  message: string;
  path: string;
  traceId?: string;
  fieldErrors?: FieldViolation[];
}

// ---- auth ----
export interface RegisterRequest {
  name: string;
  email: string;
  countryCode: string;
  phone: string;
  password: string;
  confirmPassword: string;
  adminCode: string | null;
}

export interface RegisterResponse {
  id: string;
  name: string;
  email: string;
  role: Role;
  status: MemberStatus;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface CurrentUser {
  id: string;
  name: string;
  email: string;
  role: Role;
  mustChangePassword: boolean;
}

/** Member as returned by the API (also what GET /auth/me returns). */
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number; // seconds
  mustChangePassword: boolean;
  member: Member;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

// ---- members ----
export interface Member {
  id: string;
  name: string;
  email: string;
  countryCode: string;
  phone: string;
  role: Role;
  status: MemberStatus;
  createdAt: string; // ISO instant
  version: number; // optimistic lock, echo it back on update and role change
}

export interface CreateMemberRequest {
  name: string;
  email: string;
  countryCode: string;
  phone: string;
  role: Role;
}

export type MemberProfile = Omit<CreateMemberRequest, 'role'>;

export interface UpdateMemberRequest extends MemberProfile {
  version: number;
}

export interface ChangeMemberRoleRequest {
  role: Role;
  version: number;
}

export interface CreateMemberResponse {
  member: Member;
  temporaryPassword: string;
}

export interface ResetPasswordResponse {
  temporaryPassword: string;
}

export interface BulkDeleteResponse {
  deletedCount: number;
}

export interface MemberQuery {
  search?: string;
  role?: Role;
  status?: MemberStatus;
  sort: string; // "field,direction", for example "name,asc"
  page: number;
  size: number;
}
