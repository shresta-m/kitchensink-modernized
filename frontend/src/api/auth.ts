import type { LoginResponse, UserAccount } from '../types/api'
import { request } from './http'

export const authApi = {
  login: (username: string, password: string) =>
    request<LoginResponse>('/auth/login', { method: 'POST', json: { username, password }, skipAuthHandling: true }),
  /** Public sign-up. The server always creates a VIEWER; there is no role to send. */
  register: (username: string, password: string) =>
    request<LoginResponse>('/auth/register', { method: 'POST', json: { username, password }, skipAuthHandling: true }),
  /** Separate admin sign-up. Needs the server's admin sign-up code and does not sign anyone in. */
  registerAdmin: (username: string, password: string, adminCode: string) =>
    request<UserAccount>('/auth/register-admin', {
      method: 'POST', json: { username, password, adminCode }, skipAuthHandling: true,
    }),
  me: () => request<LoginResponse['user']>('/auth/me'),
}
