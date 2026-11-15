import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/http'
import { authApi } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import { ErrorAlert } from '../components/Alert'
import { AuthShell } from '../components/AuthShell'
import { TextField } from '../components/TextField'
import { useToast } from '../components/Toast'
import { validateCredentials, type CredentialErrors } from '../utils/validation'

export function RegisterPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errors, setErrors] = useState<CredentialErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/members" replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setFormError(null)
    const found = validateCredentials(username, password, confirm)
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setBusy(true)
    try {
      await authApi.register(username.trim(), password)
      toast('Account created. Sign in to continue.')
      navigate('/login', { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        const fields = err.fieldErrors()
        setErrors({ username: fields.username, password: fields.password })
        if (!fields.username && !fields.password) setFormError(err.message)
      } else {
        setFormError('Could not create the account. Please try again.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell>
      <form onSubmit={submit} noValidate className="flex w-full max-w-sm flex-col gap-5">
        <div>
          <h2 className="m-0 text-2xl font-bold tracking-tight">Create account</h2>
          <p className="mb-0 mt-1.5 text-[15px] text-ink-muted">
            New accounts get read-only access and sign in afterwards. An administrator can create accounts with edit rights.
          </p>
        </div>
        {formError && <ErrorAlert>{formError}</ErrorAlert>}
        <TextField label="Username" value={username} onChange={(e) => setUsername(e.target.value)}
          error={errors.username} hint="3 to 50 characters: letters, digits, dot, underscore, hyphen"
          autoComplete="username" autoFocus disabled={busy} />
        <TextField label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
          error={errors.password} hint="At least 12 characters" autoComplete="new-password" disabled={busy} />
        <TextField label="Confirm password" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)}
          error={errors.confirm} autoComplete="new-password" disabled={busy} />
        <button type="submit" className="btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
        <p className="m-0 text-center text-sm text-ink-muted">
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-brand hover:text-brand-dark">Sign in</Link>
        </p>
      </form>
    </AuthShell>
  )
}
