import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import { AuthLayout } from '../components/AuthLayout';
import { Alert, Button, Field, PasswordInput, describedBy } from '../components/ui';
import { check, onlyErrors } from '../lib/validation';
import { parseFormError } from '../lib/formErrors';

export function ChangePasswordPage() {
  const { user, clearSession } = useAuth();
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const show = (k: string) => fields[k];

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const local = onlyErrors({
      currentPassword: check.filled(current, 'Enter your current password.'),
      newPassword: check.filled(next, 'Enter a new password.'),
      confirmPassword: check.filled(confirm, 'Confirm the new password.'),
    });
    if (Object.keys(local).length > 0) {
      setFields(local);
      return;
    }
    setBusy(true);
    setFormError(null);
    setFields({});
    try {
      await authApi.changePassword({ currentPassword: current, newPassword: next, confirmPassword: confirm });
      // The server ends every session when the password changes, this one included: sign in again with the new password.
      clearSession();
      navigate('/login', { replace: true, state: { passwordChanged: true } });
    } catch (err) {
      const parsed = parseFormError(err, ['currentPassword', 'newPassword', 'confirmPassword']);
      setFields(parsed.fields);
      setFormError(parsed.general);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      heading="Set a new password"
      intro={user?.mustChangePassword ? 'You signed in with a temporary password. Choose your own to continue.' : 'Choose a new password for your account.'}
      bullets={['8 to 72 characters', 'At least one letter, one number and one special character (for example @ # $ !)', 'Different from your current password', 'You will be signed out everywhere and sign in again with the new one']}
    >
      <form onSubmit={submit} noValidate className="flex w-full max-w-[460px] flex-col gap-4">
        <h2 className="text-[28px] font-bold tracking-tight">Change password</h2>
        {formError && <Alert tone="error">{formError}</Alert>}
        <Field label={user?.mustChangePassword ? 'Temporary password' : 'Current password'} htmlFor="cp-current" error={show('currentPassword')} required>
          <PasswordInput id="cp-current" autoComplete="current-password" value={current} invalid={!!show('currentPassword')} aria-describedby={describedBy('cp-current', show('currentPassword'))} onChange={(e) => setCurrent(e.target.value)} />
        </Field>
        <Field label="New password" htmlFor="cp-new" error={show('newPassword')} hint="8 to 72 characters, with a letter, a number and a special character (for example @ # $ !)." required>
          <PasswordInput id="cp-new" autoComplete="new-password" value={next} invalid={!!show('newPassword')} aria-describedby={describedBy('cp-new', show('newPassword'), 'x')} onChange={(e) => setNext(e.target.value)} />
        </Field>
        <Field label="Confirm new password" htmlFor="cp-confirm" error={show('confirmPassword')} required>
          <PasswordInput id="cp-confirm" autoComplete="new-password" value={confirm} invalid={!!show('confirmPassword')} aria-describedby={describedBy('cp-confirm', show('confirmPassword'))} onChange={(e) => setConfirm(e.target.value)} />
        </Field>
        <Button type="submit" size="lg" disabled={busy}>
          {busy ? 'Saving…' : 'Save new password'}
        </Button>
      </form>
    </AuthLayout>
  );
}
