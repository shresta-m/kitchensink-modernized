import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { AuthLayout } from '../components/AuthLayout';
import { Alert, Button, Field, PasswordInput, PhoneInput, TextInput, describedBy } from '../components/ui';
import { EMPTY_RESPONSE_MESSAGE } from '../lib/errors';
import { check, onlyErrors } from '../lib/validation';
import { parseFormError } from '../lib/formErrors';

export function RegisterPage() {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [countryCode, setCountryCode] = useState('+91');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [adminCode, setAdminCode] = useState('');
  const [serverFields, setServerFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const err = (k: string) => serverFields[k];

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const local = onlyErrors({
      name: check.filled(name, 'Enter the full name.'),
      email: check.email(email),
      phone: check.phone(phone),
      password: check.filled(password, 'Enter a password.'),
      confirmPassword: check.filled(confirm, 'Confirm the password.'),
    });
    if (Object.keys(local).length > 0) {
      setServerFields(local);
      return;
    }
    setBusy(true);
    setFormError(null);
    setServerFields({});
    try {
      const res = await authApi.register({
        name: name.trim(),
        email: email.trim().toLowerCase(),
        countryCode,
        phone: phone.replace(/\s/g, ''),
        password,
        confirmPassword: confirm,
        adminCode: adminCode.trim() || null,
      });
      if (!res) {
        setFormError(EMPTY_RESPONSE_MESSAGE);
        return;
      }
      navigate('/login', { state: { registered: res.role, pending: res.status === 'PENDING_APPROVAL' } });
    } catch (e) {
      const parsed = parseFormError(e, ['name', 'email', 'countryCode', 'phone', 'password', 'confirmPassword', 'adminCode']);
      setServerFields({ ...parsed.fields, ...(parsed.fields.countryCode && !parsed.fields.phone ? { phone: parsed.fields.countryCode } : {}) });
      setFormError(parsed.general);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      heading="Create your account"
      intro="Register to view the member directory. An administrator approves new accounts before you can sign in. Have an admin code? Add it to get administrator access."
      bullets={['Accounts are approved by an administrator', 'Admin access only with a valid code', 'Your data stays on this server']}
    >
      <form onSubmit={submit} noValidate className="grid w-full max-w-[640px] grid-cols-1 gap-x-4 gap-y-3 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <h2 className="text-[28px] font-bold tracking-tight">Register</h2>
          <p className="mt-1 text-[15px] text-gray-600">
            Already registered?{' '}
            <Link to="/login" className="font-semibold text-teal-800 underline">
              Sign in
            </Link>
          </p>
        </div>
        {formError && <div className="sm:col-span-2"><Alert tone="error">{formError}</Alert></div>}

        <Field label="Full name" htmlFor="name" error={err('name')} hint="Up to 25 characters, no digits." required>
          <TextInput id="name" autoComplete="name" value={name} invalid={!!err('name')} aria-describedby={describedBy('name', err('name'), 'x')} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Email" htmlFor="email" error={err('email')} hint="For example name@company.com." required>
          <TextInput id="email" type="email" autoComplete="email" value={email} invalid={!!err('email')} aria-describedby={describedBy('email', err('email'), 'x')} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Phone number" htmlFor="phone" error={err('phone')} hint="Digits only, without the country code." required>
          <PhoneInput id="phone" countryCode={countryCode} phone={phone} onCountryCode={setCountryCode} onPhone={setPhone} invalid={!!err('phone')} describedById={describedBy('phone', err('phone'), 'x')} />
        </Field>
        <Field label="Admin code" htmlFor="adminCode" error={err('adminCode')} hint="Leave empty to register as a viewer." optional>
          <TextInput id="adminCode" placeholder="XXXX-XXXX-XXXX" autoCapitalize="characters" spellCheck={false} value={adminCode} invalid={!!err('adminCode')} aria-describedby={describedBy('adminCode', err('adminCode'), 'x')} onChange={(e) => setAdminCode(e.target.value.toUpperCase())} />
        </Field>
        <Field label="Password" htmlFor="password" error={err('password')} hint="8 to 72 characters with a letter, a number and a special character." required>
          <PasswordInput id="password" autoComplete="new-password" value={password} invalid={!!err('password')} aria-describedby={describedBy('password', err('password'), 'x')} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Field label="Confirm password" htmlFor="confirm" error={err('confirmPassword')} required>
          <PasswordInput id="confirm" autoComplete="new-password" value={confirm} invalid={!!err('confirmPassword')} aria-describedby={describedBy('confirm', err('confirmPassword'))} onChange={(e) => setConfirm(e.target.value)} />
        </Field>

        <Button type="submit" size="lg" disabled={busy} className="mt-1 sm:col-span-2">
          {busy ? 'Creating account…' : 'Create account'}
        </Button>
      </form>
    </AuthLayout>
  );
}
