import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/http'
import { authApi } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import { ErrorAlert } from '../components/Alert'
import { AuthShell } from '../components/AuthShell'
import { TextField } from '../components/TextField'
import { useToast } from '../components/Toast'
import { validateAdminSignup, type CredentialErrors } from '../utils/validation'

/** Separate, unlinked sign-up page for administrators. Needs the server's admin sign-up code. */
export function AdminRegisterPage() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [adminCode, setAdminCode] = useState('')
  const [errors, setErrors] = useState<CredentialErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setFormError(null)
    const found = validateAdminSignup(username, password, confirm, adminCode)
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setBusy(true)
    try {
      await authApi.registerAdmin(username.trim(), password, adminCode.trim())
      logout() // never leave a previous session (e.g. a viewer) active under the new admin sign-in
      toast('Admin account created. Sign in to continue.')
      navigate('/login', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setFormError('Admin sign-up is not enabled on this server.')
      } else if (err instanceof ApiError) {
        const fields = err.fieldErrors()
        setErrors({ username: fields.username, password: fields.password, adminCode: fields.adminCode })
        if (!fields.username && !fields.password && !fields.adminCode) setFormError(err.message)
      } else {
        setFormError('Could not create the admin account. Please try again.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell>
      <form onSubmit={submit} noValidate className="flex w-full max-w-sm flex-col gap-5">
        <div>
          <h2 className="m-0 text-2xl font-bold tracking-tight">Create admin account</h2>
          <p className="mb-0 mt-1.5 text-[15px] text-ink-muted">
            Admins can add, edit, delete and import members and manage users. You need the admin sign-up code.
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
        <TextField label="Admin sign-up code" type="password" value={adminCode}
          onChange={(e) => setAdminCode(e.target.value)} error={errors.adminCode} autoComplete="off" disabled={busy} />
        <button type="submit" className="btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create admin account'}</button>
        <p className="m-0 text-center text-sm text-ink-muted">
          Back to{' '}
          <Link to="/login" className="font-semibold text-brand hover:text-brand-dark">Sign in</Link>
        </p>
      </form>
    </AuthShell>
  )
}
