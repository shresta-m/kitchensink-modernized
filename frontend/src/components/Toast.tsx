import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'

interface ToastItem {
  id: number
  message: string
  tone: 'success' | 'error'
}

const ToastContext = createContext<(message: string, tone?: ToastItem['tone']) => void>(() => {})

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const nextId = useRef(0)

  const show = useCallback((message: string, tone: ToastItem['tone'] = 'success') => {
    const id = nextId.current++
    setItems((prev) => [...prev, { id, message, tone }])
    setTimeout(() => setItems((prev) => prev.filter((t) => t.id !== id)), tone === 'error' ? 6000 : 3500)
  }, [])

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className="pointer-events-none fixed bottom-6 right-6 z-[60] flex flex-col gap-2" aria-live="polite">
        {items.map((t) => (
          <div
            key={t.id}
            role="status"
            className={`pointer-events-auto flex max-w-sm items-center gap-3 rounded-[10px] border bg-white px-4 py-3.5 text-sm shadow-lg ${
              t.tone === 'error' ? 'border-danger-line' : 'border-brand-line'
            }`}
          >
            <span className={`h-2 w-2 shrink-0 rounded-full ${t.tone === 'error' ? 'bg-danger' : 'bg-brand'}`} />
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export const useToast = () => useContext(ToastContext)
