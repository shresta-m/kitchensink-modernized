import { useState, type FormEvent } from 'react';
import { membersApi } from '../api/members';
import type { Member, Role } from '../api/types';
import { Modal } from '../components/Modal';
import { Alert, Button, Field, PhoneInput, SelectInput, TextInput, describedBy } from '../components/ui';
import { useLookups } from '../lib/useLookups';
import { EMPTY_RESPONSE_MESSAGE } from '../lib/errors';
import { check, onlyErrors } from '../lib/validation';
import { parseFormError } from '../lib/formErrors';

export function EditMemberDialog({
  member,
  onClose,
  onSaved,
  onResetPassword,
}: {
  member: Member | null;
  onClose: () => void;
  onSaved: (m: Member) => void;
  onResetPassword: (m: Member) => void;
}) {
  return (
    <Modal open={member !== null} onClose={onClose} title="Edit member" description="Update details or change the role.">
      {member && <EditForm key={member.id} member={member} onClose={onClose} onSaved={onSaved} onResetPassword={onResetPassword} />}
    </Modal>
  );
}

function EditForm({
  member,
  onClose,
  onSaved,
  onResetPassword,
}: {
  member: Member;
  onClose: () => void;
  onSaved: (m: Member) => void;
  onResetPassword: (m: Member) => void;
}) {
  const { roles } = useLookups();
  const [name, setName] = useState(member.name);
  const [email, setEmail] = useState(member.email);
  const [countryCode, setCountryCode] = useState(member.countryCode);
  const [phone, setPhone] = useState(member.phone);
  const [role, setRole] = useState<Role>(member.role);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const show = (k: string) => fields[k];

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
      const profileChanged =
        name.trim() !== member.name ||
        email.trim().toLowerCase() !== member.email ||
        countryCode !== member.countryCode ||
        phone.replace(/\s/g, '') !== member.phone;
      let saved: Member | undefined = member;
      if (profileChanged) {
        saved = await membersApi.update(member.id, {
          name: name.trim(),
          email: email.trim().toLowerCase(),
          countryCode,
          phone: phone.replace(/\s/g, ''),
          version: member.version,
        });
        if (!saved) return setFormError(EMPTY_RESPONSE_MESSAGE);
      }
      if (role !== member.role) {
        try {
          saved = await membersApi.changeRole(member.id, { role, version: saved.version });
        } catch (err) {
          setFormError(`${profileChanged ? 'Details were saved, but the role change failed. ' : ''}${parseFormError(err, []).general ?? ''}`);
          return;
        }
        if (!saved) return setFormError(EMPTY_RESPONSE_MESSAGE);
      }
      onSaved(saved);
    } catch (err) {
      const parsed = parseFormError(err, ['name', 'email', 'countryCode', 'phone']);
      setFields({ ...parsed.fields, ...(parsed.fields.countryCode && !parsed.fields.phone ? { phone: parsed.fields.countryCode } : {}) });
      setFormError(parsed.general);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      {formError && <Alert tone="error">{formError}</Alert>}
      <Field label="Full name" htmlFor="edit-name" error={show('name')} hint="Up to 25 characters, no digits." required>
        <TextInput id="edit-name" value={name} invalid={!!show('name')} aria-describedby={describedBy('edit-name', show('name'), 'x')} onChange={(e) => setName(e.target.value)} />
      </Field>
      <Field label="Email" htmlFor="edit-email" error={show('email')} hint="For example name@company.com." required>
        <TextInput id="edit-email" type="email" value={email} invalid={!!show('email')} aria-describedby={describedBy('edit-email', show('email'), 'x')} onChange={(e) => setEmail(e.target.value)} />
      </Field>
      <Field label="Phone number" htmlFor="edit-phone" error={show('phone')} hint="Digits only, without the country code." required>
        <PhoneInput id="edit-phone" countryCode={countryCode} phone={phone} onCountryCode={setCountryCode} onPhone={setPhone} invalid={!!show('phone')} describedById={describedBy('edit-phone', show('phone'), 'x')} />
      </Field>
      <Field label="Role" htmlFor="edit-role" required>
        <SelectInput id="edit-role" value={role} onChange={(e) => setRole(e.target.value as Role)}>
          {roles.map((r) => (
            <option key={r.value} value={r.value}>{r.label}</option>
          ))}
        </SelectInput>
      </Field>
      <div className="flex items-center justify-between gap-2.5 pt-1">
        {member.role === 'ADMIN' ? (
          <span className="max-w-[220px] text-xs text-gray-600">An administrator&apos;s password cannot be reset. Change the role to Viewer first.</span>
        ) : (
          <button type="button" className="text-sm font-semibold text-teal-800 underline" onClick={() => onResetPassword(member)} disabled={busy}>
            Reset password
          </button>
        )}
        <div className="flex gap-2.5">
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy}>
            {busy ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      </div>
    </form>
  );
}
