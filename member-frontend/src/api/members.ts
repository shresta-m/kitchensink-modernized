import { request, requestBlob } from './client';
import type {
  BulkDeleteResponse,
  ChangeMemberRoleRequest,
  CreateMemberRequest,
  CreateMemberResponse,
  Member,
  MemberQuery,
  PageResponse,
  ResetPasswordResponse,
  UpdateMemberRequest,
} from './types';

type Filters = Pick<MemberQuery, 'search' | 'role' | 'status'>;

export const membersApi = {
  list: (q: MemberQuery) =>
    request<PageResponse<Member>>('/members', {
      query: { search: q.search, role: q.role, status: q.status, sort: q.sort, page: q.page, size: q.size },
    }),

  get: (id: string) => request<Member>(`/members/${id}`),

  create: (body: CreateMemberRequest) => request<CreateMemberResponse>('/members', { method: 'POST', body }),

  update: (id: string, body: UpdateMemberRequest) => request<Member>(`/members/${id}`, { method: 'PUT', body }),

  changeRole: (id: string, body: ChangeMemberRoleRequest) =>
    request<Member>(`/members/${id}/role`, { method: 'PUT', body }),

  remove: (id: string) => request<void>(`/members/${id}`, { method: 'DELETE' }),

  bulkDelete: (ids: string[]) => request<BulkDeleteResponse>('/members/bulk-delete', { method: 'POST', body: { ids } }),

  approve: (id: string) => request<Member>(`/members/${id}/approve`, { method: 'POST' }),

  resetPassword: (id: string) => request<ResetPasswordResponse>(`/members/${id}/reset-password`, { method: 'POST' }),

  exportCsv: (f: Filters) =>
    requestBlob('/members/export', { search: f.search, role: f.role, status: f.status }, 'members.csv'),
};
