import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import { AuthLayout } from '../components/AuthLayout';
import { Alert, Button, Field, PasswordInput, TextInput, describedBy } from '../components/ui';
import { EMPTY_RESPONSE_MESSAGE } from '../lib/errors';
import { check, onlyErrors } from '../lib/validation';
import { parseFormError } from '../lib/formErrors';

export function LoginPage() {
  const { user, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const navState = location.state as { registered?: string; pending?: boolean; passwordChanged?: boolean } | null;
  const registered = navState?.registered;
  const pending = navState?.pending === true;
  const passwordChanged = navState?.passwordChanged === true;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  if (user) return <Navigate to={user.mustChangePassword ? '/change-password' : '/members'} replace />;

  const show = (k: string) => fields[k];

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const local = onlyErrors({
      email: check.filled(email, 'Enter your email.'),
      password: check.filled(password, 'Enter your password.'),
    });
    if (Object.keys(local).length > 0) {
      setFields(local);
      return;
    }
    setBusy(true);
    setFormError(null);
    setFields({});
    try {
      const res = await authApi.login({ email: email.trim().toLowerCase(), password });
      if (!res) {
        setFormError(EMPTY_RESPONSE_MESSAGE);
        return;
      }
      signIn(res);
      navigate(res.mustChangePassword ? '/change-password' : '/members', { replace: true });
    } catch (err) {
      const parsed = parseFormError(err, ['email', 'password']);
      setFields(parsed.fields);
      setFormError(parsed.general);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      heading="Welcome back"
      intro="Sign in with your email and password. Administrators and viewers use the same page."
      bullets={['Admins manage members', 'Viewers browse the directory', 'First sign-in with a temporary password? You will set a new one']}
    >
      <form onSubmit={submit} noValidate className="flex w-full max-w-[460px] flex-col gap-4">
        <div>
          <h2 className="text-[28px] font-bold tracking-tight">Sign in</h2>
          <p className="mt-1 text-[15px] text-gray-600">
            New here?{' '}
            <Link to="/register" className="font-semibold text-teal-800 underline">
              Create an account
            </Link>
          </p>
        </div>
        {registered && (
          <Alert tone="success">
            {pending ? 'Registration received. An administrator must approve your account before you can sign in.' : `Account created${registered === 'ADMIN' ? ' with administrator access' : ''}. Sign in to continue.`}
          </Alert>
        )}
        {passwordChanged && <Alert tone="success">Password changed. You were signed out everywhere. Sign in with your new password.</Alert>}
        {formError && <Alert tone="error">{formError}</Alert>}
        <Field label="Email" htmlFor="login-email" error={show('email')} required>
          <TextInput id="login-email" type="email" autoComplete="username" value={email} invalid={!!show('email')} aria-describedby={describedBy('login-email', show('email'))} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Password" htmlFor="login-password" error={show('password')} required>
          <PasswordInput id="login-password" autoComplete="current-password" value={password} invalid={!!show('password')} aria-describedby={describedBy('login-password', show('password'))} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <p className="text-[13px] text-gray-600">Forgot your password? Ask an administrator to reset it for you.</p>
        <Button type="submit" size="lg" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </AuthLayout>
  );
}
