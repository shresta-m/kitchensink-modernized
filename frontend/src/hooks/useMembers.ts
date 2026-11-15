import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { membersApi } from '../api/members'
import type { MemberInput, MemberQuery } from '../types/api'

const KEY = ['members'] as const

export const useMembersList = (query: MemberQuery) =>
  useQuery({
    queryKey: [...KEY, query],
    queryFn: ({ signal }) => membersApi.list(query, signal),
    placeholderData: keepPreviousData, // no table flicker while paging or sorting
  })

function useInvalidate() {
  const qc = useQueryClient()
  return () => qc.invalidateQueries({ queryKey: KEY })
}

export function useCreateMember() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (input: MemberInput) => membersApi.create(input), onSuccess: invalidate })
}

export function useUpdateMember() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (v: { id: string; input: MemberInput; version: number }) =>
      membersApi.update(v.id, v.input, v.version),
    onSuccess: invalidate,
  })
}

export function useDeleteMember() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (id: string) => membersApi.remove(id), onSuccess: invalidate })
}

export function useBulkDelete() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (ids: string[]) => membersApi.bulkDelete(ids), onSuccess: invalidate })
}

export function useImportMembers() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (file: File) => membersApi.importXlsx(file), onSuccess: invalidate })
}
