import type { Role, UserAccount } from '../types/api'
import { request } from './http'

export interface CreateUserInput {
  username: string
  password: string
  role: Role
}

/** Administrator-only endpoints. */
export const usersApi = {
  list: () => request<UserAccount[]>('/admin/users'),
  create: (input: CreateUserInput) => request<UserAccount>('/admin/users', { method: 'POST', json: input }),
}
