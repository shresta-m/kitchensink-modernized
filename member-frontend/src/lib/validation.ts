// Presentation constants only. Every validation rule lives in the backend (MemberRules.java, PhoneRules.java);
// the forms show the backend's field errors and keep only short captions as guidance.

export const DIAL_CODES = [
  { code: '+91', label: '+91 IN' },
  { code: '+1', label: '+1 US' },
  { code: '+44', label: '+44 UK' },
  { code: '+971', label: '+971 AE' },
  { code: '+65', label: '+65 SG' },
  { code: '+61', label: '+61 AU' },
  { code: '+49', label: '+49 DE' },
] as const;

// ---- basic client checks: required and rough shape only. The backend stays the source of truth. ----

export const PHONE_MIN_DIGITS = 8;
export const PHONE_MAX_DIGITS = 12;
const BASIC_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type Rule = string | undefined;

export const check = {
  /** Not empty and not just spaces. */
  filled: (v: string, message: string): Rule => (v.trim() ? undefined : message),
  email: (v: string): Rule =>
    !v.trim() ? 'Enter the email address.' : BASIC_EMAIL.test(v.trim()) ? undefined : 'Enter a valid email address.',
  phone: (v: string): Rule => {
    const digits = v.replace(/\s/g, '');
    if (!digits) return 'Enter the phone number.';
    return new RegExp(`^\\d{${PHONE_MIN_DIGITS},${PHONE_MAX_DIGITS}}$`).test(digits)
      ? undefined
      : `Phone number must be ${PHONE_MIN_DIGITS} to ${PHONE_MAX_DIGITS} digits.`;
  },
};

/** Drops the fields that passed, leaving only messages to show. */
export function onlyErrors(all: Record<string, Rule>): Record<string, string> {
  return Object.fromEntries(Object.entries(all).filter((e): e is [string, string] => Boolean(e[1])));
}
