import { AlertCircle } from 'lucide-react'
import type { ReactNode } from 'react'

export function ErrorAlert({ children }: { children: ReactNode }) {
  return (
    <div role="alert" className="flex items-start gap-2.5 rounded-lg border border-danger-line bg-danger-tint px-3.5 py-3 text-sm text-danger-dark">
      <AlertCircle size={18} className="mt-px shrink-0" />
      <div>{children}</div>
    </div>
  )
}
