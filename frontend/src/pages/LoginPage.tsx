import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/http'
import { useAuth } from '../auth/AuthContext'
import { ErrorAlert } from '../components/Alert'
import { AuthShell } from '../components/AuthShell'
import { TextField } from '../components/TextField'

export function LoginPage() {
  const { user, login, sessionExpired } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/members'

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={from} replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!username.trim() || !password) {
      setError('Enter your username and password.')
      return
    }
    setError(null)
    setBusy(true)
    try {
      await login(username.trim(), password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Sign-in failed. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell>
      <form onSubmit={submit} noValidate className="flex w-full max-w-sm flex-col gap-5">
        <div>
          <h2 className="m-0 text-2xl font-bold tracking-tight">Sign in</h2>
          <p className="mb-0 mt-1.5 text-[15px] text-ink-muted">Welcome back. Enter your details to continue.</p>
        </div>
        {sessionExpired && !error && <ErrorAlert>Your session expired. Please sign in again.</ErrorAlert>}
        {error && <ErrorAlert>{error}</ErrorAlert>}
        <TextField label="Username" value={username} onChange={(e) => setUsername(e.target.value)}
          autoComplete="username" autoFocus disabled={busy} />
        <TextField label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password" disabled={busy} />
        <button type="submit" className="btn-primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <p className="m-0 text-center text-sm text-ink-muted">
          New here?{' '}
          <Link to="/register" className="font-semibold text-brand hover:text-brand-dark">Create an account</Link>
        </p>
      </form>
    </AuthShell>
  )
}
