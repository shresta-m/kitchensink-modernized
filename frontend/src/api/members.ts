import type {
  BulkDeleteResponse,
  ImportReport,
  Member,
  MemberInput,
  MemberQuery,
  PageResponse,
} from '../types/api'
import { download, request } from './http'

const sortParam = (q: Pick<MemberQuery, 'sortField' | 'sortDir'>) => `${q.sortField},${q.sortDir}`

export const membersApi = {
  list: (q: MemberQuery, signal?: AbortSignal) =>
    request<PageResponse<Member>>('/members', {
      query: { q: q.q.trim(), page: q.page, size: q.size, sort: sortParam(q) },
      signal,
    }),

  create: (input: MemberInput) => request<Member>('/members', { method: 'POST', json: input }),

  update: (id: string, input: MemberInput, version: number) =>
    request<Member>(`/members/${encodeURIComponent(id)}`, { method: 'PUT', json: { ...input, version } }),

  remove: (id: string) => request<void>(`/members/${encodeURIComponent(id)}`, { method: 'DELETE' }),

  bulkDelete: (ids: string[]) =>
    request<BulkDeleteResponse>('/members/bulk-delete', { method: 'POST', json: { ids } }),

  importXlsx: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<ImportReport>('/members/import', { method: 'POST', form })
  },

  downloadTemplate: () => download('/members/import/template', 'members-template.xlsx'),

  exportCsv: (q: Pick<MemberQuery, 'q' | 'sortField' | 'sortDir'>) =>
    download('/members/export', 'members.csv', { q: q.q.trim(), sort: sortParam(q) }),
}
