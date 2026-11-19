import { request } from './client';
import type {
  ChangePasswordRequest,
  CurrentUser,
  LoginRequest,
  LoginResponse,
  Member,
  RegisterRequest,
  RegisterResponse,
} from './types';

export const toCurrentUser = (m: Member, mustChangePassword: boolean): CurrentUser => ({
  id: m.id,
  name: m.name,
  email: m.email,
  role: m.role,
  mustChangePassword,
});

export const authApi = {
  register: (body: RegisterRequest) => request<RegisterResponse>('/auth/register', { method: 'POST', body }),

  login: (body: LoginRequest) => request<LoginResponse>('/auth/login', { method: 'POST', body }),

  me: async (): Promise<CurrentUser | undefined> => {
    const m = await request<Member>('/auth/me');
    return m && toCurrentUser(m, m.status === 'TEMP_PASSWORD');
  },

  /** Ends every session of this member on the server, not just this browser's copy of the token. */
  logout: () => request<void>('/auth/logout', { method: 'POST' }),

  changePassword: (body: ChangePasswordRequest) => request<void>('/auth/change-password', { method: 'POST', body }),
};
