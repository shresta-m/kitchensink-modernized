import { useId, type InputHTMLAttributes } from 'react'

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  hint?: string
}

export function TextField({ label, error, hint, className = '', ...props }: TextFieldProps) {
  const id = useId()
  const describedBy = error ? `${id}-err` : hint ? `${id}-hint` : undefined
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-semibold text-ink-soft">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`input ${error ? 'input-error' : ''} ${className}`}
        {...props}
      />
      {error ? (
        <p id={`${id}-err`} className="mb-0 mt-1.5 text-[13px] text-danger-dark">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="mb-0 mt-1.5 text-[13px] text-ink-muted">
          {hint}
        </p>
      ) : null}
    </div>
  )
}
