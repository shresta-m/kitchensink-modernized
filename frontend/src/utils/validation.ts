import type { MemberInput } from '../types/api'

/**
 * Mirrors the backend rules (MemberRules) so users get instant feedback.
 * The server stays the authority: it re-validates everything and its field errors are shown too.
 */
export const RULES = {
  nameMax: 25,
  emailMax: 254,
  emailRegex: /^.+@.+\..+$/,
  phoneRegex: /^\d{10,12}$/,
}

export type FieldErrors = Partial<Record<keyof MemberInput, string>>

export function validateMember(input: MemberInput): FieldErrors {
  const errors: FieldErrors = {}
  const name = input.name.trim()
  const email = input.email.trim()
  const phone = input.phoneNumber.trim()

  if (!name) errors.name = 'Name is required'
  else if (name.length > RULES.nameMax) errors.name = `Must be at most ${RULES.nameMax} characters`
  else if (/\d/.test(name)) errors.name = 'Must not contain digits'

  if (!email) errors.email = 'Email is required'
  else if (email.length > RULES.emailMax) errors.email = `Must be at most ${RULES.emailMax} characters`
  else if (!RULES.emailRegex.test(email)) errors.email = 'Must be a valid email address'

  if (!phone) errors.phoneNumber = 'Phone number is required'
  else if (!RULES.phoneRegex.test(phone)) errors.phoneNumber = 'Must be 10 to 12 digits'

  return errors
}

export const hasErrors = (e: FieldErrors) => Object.keys(e).length > 0

export const CREDENTIAL_RULES = {
  usernameRegex: /^[A-Za-z0-9._-]{3,50}$/,
  passwordMin: 12,
  passwordMax: 128,
}

export interface CredentialErrors {
  username?: string
  password?: string
  confirm?: string
  adminCode?: string
}

/** Mirrors the backend UserRules. Pass `confirm` only where a confirmation field exists. */
export function validateCredentials(username: string, password: string, confirm?: string): CredentialErrors {
  const errors: CredentialErrors = {}
  if (!username.trim()) errors.username = 'Username is required'
  else if (!CREDENTIAL_RULES.usernameRegex.test(username.trim()))
    errors.username = '3 to 50 characters: letters, digits, dot, underscore or hyphen'

  if (!password) errors.password = 'Password is required'
  else if (password.length < CREDENTIAL_RULES.passwordMin)
    errors.password = `Must be at least ${CREDENTIAL_RULES.passwordMin} characters`
  else if (password.length > CREDENTIAL_RULES.passwordMax)
    errors.password = `Must be at most ${CREDENTIAL_RULES.passwordMax} characters`

  if (confirm !== undefined && !errors.password && confirm !== password) errors.confirm = 'Passwords do not match'
  return errors
}

/** Admin sign-up: the normal credential rules plus the secret code. */
export function validateAdminSignup(username: string, password: string, confirm: string, adminCode: string): CredentialErrors {
  const errors = validateCredentials(username, password, confirm)
  if (!adminCode.trim()) errors.adminCode = 'Admin sign-up code is required'
  return errors
}
