import { useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react';
import { DIAL_CODES } from '../lib/validation';
import { Icon } from './Icon';

export function cn(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(' ');
}

// ---------- Button ----------
type Variant = 'primary' | 'secondary' | 'outline' | 'danger';
type Size = 'sm' | 'md' | 'lg';

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-teal-700 text-white hover:bg-teal-800',
  secondary: 'border border-[#8A8F98] bg-white text-gray-900 hover:bg-gray-50',
  outline: 'border border-teal-700 bg-white text-teal-800 hover:bg-teal-50',
  danger: 'bg-red-700 text-white hover:bg-red-800',
};
const SIZES: Record<Size, string> = {
  sm: 'h-9 px-3.5 text-[13px]',
  md: 'h-11 px-5 text-sm',
  lg: 'h-12 px-6 text-base',
};

export function Button({
  variant = 'primary',
  size = 'md',
  type = 'button',
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: Size }) {
  return (
    <button
      type={type}
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-lg font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-700 disabled:cursor-not-allowed disabled:opacity-60',
        VARIANTS[variant],
        SIZES[size],
        className,
      )}
      {...props}
    />
  );
}

// ---------- Fields ----------
const inputClass = (invalid?: boolean) =>
  cn(
    'h-11 w-full rounded-lg bg-white px-3.5 text-[15px] text-gray-900 placeholder:text-gray-500 focus:outline-2 focus:outline-offset-1 focus:outline-teal-700',
    invalid ? 'border-2 border-red-800' : 'border border-[#8A8F98]',
  );

export function describedBy(id: string, error?: string, hint?: string): string | undefined {
  return [hint ? `${id}-hint` : '', error ? `${id}-err` : ''].filter(Boolean).join(' ') || undefined;
}

export function Field({
  label,
  htmlFor,
  error,
  hint,
  required,
  optional,
  children,
}: {
  label: string;
  htmlFor: string;
  error?: string;
  hint?: string;
  required?: boolean;
  optional?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="flex items-center justify-between text-sm font-semibold">
        <span>
          {label}
          {required && (
            <span aria-hidden="true" className="ml-0.5 text-red-800">
              *
            </span>
          )}
        </span>
        {optional && <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">Optional</span>}
      </label>
      {children}
      {hint && (
        <p id={`${htmlFor}-hint`} className="text-[13px] text-gray-600">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${htmlFor}-err`} className="flex items-center gap-1.5 text-[13px] font-medium text-red-800">
          <Icon name="alert" size={16} />
          {error}
        </p>
      )}
    </div>
  );
}

export function TextInput({ invalid, className, ...props }: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return <input aria-invalid={invalid || undefined} className={cn(inputClass(invalid), className)} {...props} />;
}

export function SelectInput({ invalid, className, children, ...props }: SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }) {
  return (
    <select aria-invalid={invalid || undefined} className={cn(inputClass(invalid), 'pr-8', className)} {...props}>
      {children}
    </select>
  );
}

export function PasswordInput({
  invalid,
  ...props
}: Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & { invalid?: boolean }) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="relative">
      <TextInput type={visible ? 'text' : 'password'} invalid={invalid} className="pr-16" {...props} />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? 'Hide password' : 'Show password'}
        className="absolute right-1.5 top-1.5 h-8 rounded-md px-2.5 text-[13px] font-semibold text-teal-800 hover:bg-teal-50 focus-visible:outline-2 focus-visible:outline-teal-700"
      >
        {visible ? 'Hide' : 'Show'}
      </button>
    </div>
  );
}

export function PhoneInput({
  id,
  countryCode,
  phone,
  onCountryCode,
  onPhone,
  invalid,
  describedById,
}: {
  id: string;
  countryCode: string;
  phone: string;
  onCountryCode: (v: string) => void;
  onPhone: (v: string) => void;
  invalid?: boolean;
  describedById?: string;
}) {
  return (
    <div className="flex gap-2">
      <SelectInput
        aria-label="Country code"
        value={countryCode}
        onChange={(e) => onCountryCode(e.target.value)}
        style={{ width: 144, flex: '0 0 144px', paddingLeft: 12, paddingRight: 28 }}
      >
        {DIAL_CODES.map((d) => (
          <option key={d.code} value={d.code}>
            {d.label}
          </option>
        ))}
      </SelectInput>
      <TextInput
        id={id}
        type="tel"
        inputMode="numeric"
        autoComplete="tel-national"
        className="min-w-0 flex-1"
        placeholder="98250 41763"
        value={phone}
        invalid={invalid}
        aria-describedby={describedById}
        onChange={(e) => onPhone(e.target.value.replace(/[^\d\s]/g, ''))}
      />
    </div>
  );
}

// ---------- Feedback ----------
const BADGES = {
  teal: 'bg-teal-50 text-teal-900',
  gray: 'bg-gray-100 text-gray-700',
  amber: 'bg-amber-100 text-amber-900',
} as const;

export function Badge({ tone, dot, children }: { tone: keyof typeof BADGES; dot?: boolean; children: ReactNode }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold', BADGES[tone])}>
      {dot && <span className="h-1.5 w-1.5 rounded-full bg-current" />}
      {children}
    </span>
  );
}

const ALERTS = {
  error: { box: 'bg-red-50 text-red-900', icon: 'alert' },
  warning: { box: 'bg-amber-100 text-amber-900', icon: 'warn' },
  info: { box: 'border border-teal-100 bg-teal-50 text-teal-900', icon: 'lock' },
  success: { box: 'bg-teal-50 text-teal-900', icon: 'check' },
} as const;

export function Alert({ tone, children }: { tone: keyof typeof ALERTS; children: ReactNode }) {
  const a = ALERTS[tone];
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={cn('flex items-start gap-2.5 rounded-lg px-3.5 py-3 text-sm leading-relaxed', a.box)}>
      <span className="mt-0.5">
        <Icon name={a.icon} />
      </span>
      <div>{children}</div>
    </div>
  );
}

export function StrengthMeter({ score }: { score: number }) {
  const color = score >= 3 ? 'bg-teal-700' : score === 2 ? 'bg-amber-500' : 'bg-red-600';
  return (
    <div className="flex gap-1" aria-hidden="true">
      {[1, 2, 3, 4].map((n) => (
        <span key={n} className={cn('h-1 flex-1 rounded-full', n <= score ? color : 'bg-gray-200')} />
      ))}
    </div>
  );
}
