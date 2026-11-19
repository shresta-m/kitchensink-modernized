import { ApiError } from '../api/client';
import { describeError } from './errors';

export interface FormErrors {
  /** Backend field errors for fields this form shows, keyed by field name. */
  fields: Record<string, string>;
  /** Message for the top of the form: everything that is not under a field. */
  general: string | null;
}

/** Splits a backend error into per-field messages and a general message. All rules live in the backend. */
export function parseFormError(e: unknown, knownFields: string[]): FormErrors {
  const fields: Record<string, string> = {};
  const other: string[] = [];
  if (e instanceof ApiError) {
    for (const f of e.fieldErrors) {
      if (knownFields.includes(f.field)) fields[f.field] ??= f.message;
      else other.push(`${f.field}: ${f.message}`);
    }
  }
  const hasFieldErrors = Object.keys(fields).length > 0;
  if (other.length > 0) return { fields, general: other.join(' ') };
  return { fields, general: hasFieldErrors ? null : describeError(e) };
}
