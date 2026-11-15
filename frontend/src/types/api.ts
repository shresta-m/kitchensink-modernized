export type Role = 'ADMIN' | 'VIEWER'

export interface FieldViolation {
  field: string
  message: string
}

/** RFC 9457 problem body returned by the backend. */
export interface Problem {
  status: number
  title?: string
  detail?: string
  code?: string
  traceId?: string
  errors?: FieldViolation[]
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: { username: string; role: Role }
}

export interface UserAccount {
  id: string
  username: string
  role: Role
  enabled: boolean
  createdAt: string | null
}

export interface Member {
  id: string
  name: string
  email: string
  phoneNumber: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface MemberInput {
  name: string
  email: string
  phoneNumber: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  sort: string
}

export type SortField = 'name' | 'email' | 'createdAt'
export type SortDir = 'asc' | 'desc'

export interface MemberQuery {
  q: string
  page: number
  size: number
  sortField: SortField
  sortDir: SortDir
}

export interface BulkDeleteResponse {
  requested: number
  deleted: number
  notFound: string[]
}

export interface ImportIssue {
  row: number
  field: string
  code: 'VALIDATION_FAILED' | 'DUPLICATE_IN_FILE' | 'DUPLICATE_EMAIL' | 'PROCESSING_ERROR'
  message: string
}

export interface ImportReport {
  totalRows: number
  inserted: number
  duplicatesSkipped: number
  invalid: number
  failed: number
  errors: ImportIssue[]
  errorsTruncated: boolean
}
