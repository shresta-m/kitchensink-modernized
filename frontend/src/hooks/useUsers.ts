import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { usersApi, type CreateUserInput } from '../api/users'

const KEY = ['users'] as const

export const useUsersList = () => useQuery({ queryKey: KEY, queryFn: usersApi.list })

export function useCreateUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateUserInput) => usersApi.create(input),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}
