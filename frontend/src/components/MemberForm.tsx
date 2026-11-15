import { useState, type FormEvent, type ReactNode } from 'react'
import { ApiError } from '../api/http'
import type { MemberInput } from '../types/api'
import { hasErrors, validateMember, type FieldErrors } from '../utils/validation'
import { ErrorAlert } from './Alert'
import { TextField } from './TextField'

interface MemberFormProps {
  id: string
  initial?: MemberInput
  /** Called with trimmed values that passed client validation. Throw ApiError to show server errors. */
  onSubmit: (input: MemberInput) => Promise<void>
  disabled?: boolean
  children?: ReactNode
}

const EMPTY: MemberInput = { name: '', email: '', phoneNumber: '' }

export function MemberForm({ id, initial = EMPTY, onSubmit, disabled, children }: MemberFormProps) {
  const [values, setValues] = useState<MemberInput>(initial)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  const set = (key: keyof MemberInput) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setValues((v) => ({ ...v, [key]: e.target.value }))
    if (errors[key]) setErrors((prev) => ({ ...prev, [key]: undefined }))
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setFormError(null)
    const trimmed: MemberInput = {
      name: values.name.trim(),
      email: values.email.trim(),
      phoneNumber: values.phoneNumber.trim(),
    }
    const found = validateMember(trimmed)
    setErrors(found)
    if (hasErrors(found)) return
    try {
      await onSubmit(trimmed)
    } catch (err) {
      if (err instanceof ApiError) {
        const fields = err.fieldErrors()
        setErrors((prev) => ({ ...prev, ...fields }))
        if (Object.keys(fields).length === 0) setFormError(err.message)
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    }
  }

  return (
    <form id={id} onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      {formError && <ErrorAlert>{formError}</ErrorAlert>}
      <TextField label="Full name" value={values.name} onChange={set('name')} error={errors.name} autoComplete="off"
        hint="Up to 25 characters, no digits" disabled={disabled} />
      <TextField label="Email" type="email" value={values.email} onChange={set('email')} error={errors.email}
        autoComplete="off" disabled={disabled} />
      <TextField label="Phone number" inputMode="numeric" value={values.phoneNumber} onChange={set('phoneNumber')}
        error={errors.phoneNumber} autoComplete="off" hint="10 to 12 digits" disabled={disabled} />
      {children}
    </form>
  )
}
