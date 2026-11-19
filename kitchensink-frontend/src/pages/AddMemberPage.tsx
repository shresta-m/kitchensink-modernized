import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { membersApi } from '../api/members';
import type { CreateMemberResponse, Role } from '../api/types';
import { AppLayout } from '../components/AppLayout';
import { Alert, Button, Field, PhoneInput, SelectInput, TextInput, describedBy } from '../components/ui';
import { TempPasswordDialog } from '../dialogs/TempPasswordDialog';
import { useLookups } from '../lib/useLookups';
import { EMPTY_RESPONSE_MESSAGE } from '../lib/errors';
import { check, onlyErrors } from '../lib/validation';
import { parseFormError } from '../lib/formErrors';
import { useNavigate } from 'react-router-dom';

export function AddMemberPage() {
  const navigate = useNavigate();
  const { roles } = useLookups();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [countryCode, setCountryCode] = useState('+91');
  const [phone, setPhone] = useState('');
  const [role, setRole] = useState<Role>('VIEWER');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [created, setCreated] = useState<CreateMemberResponse | null>(null);

  const show = (k: string) => fields[k];

  const reset = () => {
    setName('');
    setEmail('');
    setPhone('');
    setRole('VIEWER');
    setFields({});
    setCreated(null);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const local = onlyErrors({
      name: check.filled(name, 'Enter the full name.'),
      email: check.email(email),
      phone: check.phone(phone),
    });
    if (Object.keys(local).length > 0) {
      setFields(local);
      return;
    }
    setBusy(true);
    setFormError(null);
    setFields({});
    try {
      const res = await membersApi.create({ name: name.trim(), email: email.trim().toLowerCase(), countryCode, phone: phone.replace(/\s/g, ''), role });
      if (!res) setFormError(EMPTY_RESPONSE_MESSAGE);
      else setCreated(res);
    } catch (err) {
      const parsed = parseFormError(err, ['name', 'email', 'countryCode', 'phone', 'role']);
      setFields({ ...parsed.fields, ...(parsed.fields.countryCode && !parsed.fields.phone ? { phone: parsed.fields.countryCode } : {}) });
      setFormError(parsed.general);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AppLayout>
      <div>
        <Link to="/members" className="text-sm font-semibold text-teal-800 underline">
          ← Back to members
        </Link>
        <h1 className="mt-3 text-[28px] font-bold tracking-tight">Add member</h1>
        <p className="mt-1 text-[15px] text-gray-600">A temporary password is generated when you save. The member sets their own at first sign-in.</p>
      </div>
      <form onSubmit={submit} noValidate className="flex max-w-[520px] flex-col gap-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <Field label="Full name" htmlFor="add-name" error={show('name')} hint="Up to 25 characters, no digits." required>
          <TextInput id="add-name" value={name} invalid={!!show('name')} aria-describedby={describedBy('add-name', show('name'), 'x')} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Email" htmlFor="add-email" error={show('email')} hint="For example name@company.com." required>
          <TextInput id="add-email" type="email" value={email} invalid={!!show('email')} aria-describedby={describedBy('add-email', show('email'), 'x')} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Phone number" htmlFor="add-phone" error={show('phone')} hint="Digits only, without the country code." required>
          <PhoneInput id="add-phone" countryCode={countryCode} phone={phone} onCountryCode={setCountryCode} onPhone={setPhone} invalid={!!show('phone')} describedById={describedBy('add-phone', show('phone'), 'x')} />
        </Field>
        <Field label="Role" htmlFor="add-role" required>
          <SelectInput id="add-role" value={role} onChange={(e) => setRole(e.target.value as Role)}>
            {roles.map((r) => (
              <option key={r.value} value={r.value}>{r.label}</option>
            ))}
          </SelectInput>
        </Field>
        {role === 'ADMIN' && <Alert tone="warning">Admins can add, edit and delete members and reset passwords.</Alert>}
        <div className="flex gap-2.5 pt-1">
          <Button type="submit" disabled={busy}>
            {busy ? 'Saving…' : 'Save member'}
          </Button>
          <Button variant="secondary" onClick={() => navigate('/members')} disabled={busy}>
            Cancel
          </Button>
        </div>
      </form>
      <TempPasswordDialog
        open={created !== null}
        title="Member added"
        name={created?.member.name ?? ''}
        email={created?.member.email ?? ''}
        password={created?.temporaryPassword ?? ''}
        onAddAnother={reset}
        onDone={() => navigate('/members')}
      />
    </AppLayout>
  );
}
