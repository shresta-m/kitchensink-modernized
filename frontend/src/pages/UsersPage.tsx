import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/http'
import { ErrorAlert } from '../components/Alert'
import { TextField } from '../components/TextField'
import { useToast } from '../components/Toast'
import { useCreateUser, useUsersList } from '../hooks/useUsers'
import type { Role } from '../types/api'
import { formatDate, initials } from '../utils/format'
import { validateCredentials, type CredentialErrors } from '../utils/validation'

export function UsersPage() {
  const { data: users, error, isLoading, refetch } = useUsersList()
  const create = useCreateUser()
  const toast = useToast()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('VIEWER')
  const [errors, setErrors] = useState<CredentialErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setFormError(null)
    const found = validateCredentials(username, password)
    setErrors(found)
    if (Object.keys(found).length > 0) return
    try {
      const created = await create.mutateAsync({ username: username.trim(), password, role })
      toast(`${created.role === 'ADMIN' ? 'Administrator' : 'Viewer'} "${created.username}" created`)
      setUsername('')
      setPassword('')
      setRole('VIEWER')
    } catch (err) {
      if (err instanceof ApiError) {
        const fields = err.fieldErrors()
        setErrors({ username: fields.username, password: fields.password })
        if (!fields.username && !fields.password) setFormError(err.message)
      } else {
        setFormError('Could not create the user. Please try again.')
      }
    }
  }

  return (
    <>
      <div>
        <h1 className="m-0 text-[28px] font-bold tracking-tight">Users</h1>
        <p className="mb-0 mt-1.5 text-[15px] text-ink-muted">
          Create administrators and viewers. People can also sign up themselves, but only as viewers.
        </p>
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,380px)_1fr]">
        <section className="card p-6">
          <h2 className="m-0 text-lg font-semibold">Create user</h2>
          <form onSubmit={submit} noValidate className="mt-4 flex flex-col gap-4">
            {formError && <ErrorAlert>{formError}</ErrorAlert>}
            <TextField label="Username" value={username} onChange={(e) => setUsername(e.target.value)}
              error={errors.username} autoComplete="off" disabled={create.isPending} />
            <TextField label="Temporary password" type="password" value={password}
              onChange={(e) => setPassword(e.target.value)} error={errors.password} hint="At least 12 characters"
              autoComplete="new-password" disabled={create.isPending} />
            <div>
              <label htmlFor="role" className="mb-1.5 block text-sm font-semibold text-ink-soft">Role</label>
              <select id="role" className="input" value={role} onChange={(e) => setRole(e.target.value as Role)}
                disabled={create.isPending}>
                <option value="VIEWER">Viewer: read-only</option>
                <option value="ADMIN">Admin: can add, edit, delete, import and manage users</option>
              </select>
            </div>
            <button type="submit" className="btn-primary" disabled={create.isPending}>
              {create.isPending ? 'Creating…' : 'Create user'}
            </button>
          </form>
        </section>

        <section className="card overflow-hidden">
          {error && !users ? (
            <div className="flex flex-col items-start gap-3 p-6">
              <ErrorAlert>{error instanceof ApiError ? error.message : 'Could not load users.'}</ErrorAlert>
              <button type="button" className="btn-neutral" onClick={() => refetch()}>Try again</button>
            </div>
          ) : isLoading ? (
            <div className="p-6" role="status" aria-label="Loading users">
              {Array.from({ length: 4 }, (_, i) => <div key={i} className="mb-3 h-11 animate-pulse rounded-lg bg-surface" />)}
            </div>
          ) : (
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="bg-surface text-left text-ink-soft">
                  <th scope="col" className="py-3 pl-5 pr-4 font-semibold">User</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Role</th>
                  <th scope="col" className="py-3 pl-4 pr-5 font-semibold">Created</th>
                </tr>
              </thead>
              <tbody>
                {(users ?? []).map((u) => (
                  <tr key={u.id} className="border-t border-line">
                    <td className="py-3 pl-5 pr-4">
                      <div className="flex items-center gap-3">
                        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-tint text-[13px] font-semibold text-brand-dark">
                          {initials(u.username)}
                        </span>
                        <span className="font-semibold">{u.username}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                        u.role === 'ADMIN' ? 'bg-brand-tint text-brand-dark' : 'bg-surface text-ink-soft'}`}>
                        {u.role}
                      </span>
                    </td>
                    <td className="py-3 pl-4 pr-5 text-ink-muted">{u.createdAt ? formatDate(u.createdAt) : '–'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>
    </>
  )
}
