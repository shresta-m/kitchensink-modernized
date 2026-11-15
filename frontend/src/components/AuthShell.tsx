import type { ReactNode } from 'react'

/** Shared split layout for the sign-in and sign-up pages: teal brand panel on the left, white form on the right. */
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-screen grid-cols-1 lg:grid-cols-2">
      <aside className="hidden flex-col justify-between bg-brand p-12 text-white lg:flex">
        <div className="flex items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white text-lg font-bold text-brand">K</span>
          <span className="text-lg font-semibold">Kitchensink</span>
        </div>
        <div>
          <h1 className="m-0 max-w-md text-4xl font-bold leading-tight tracking-tight">Member management, simplified.</h1>
          <p className="mt-4 max-w-md text-base text-white/80">
            Register, search and maintain your members. Import hundreds at once from Excel.
          </p>
        </div>
        <p className="m-0 text-sm text-white/70">Spring Boot · MongoDB · React</p>
      </aside>
      <main className="flex items-center justify-center bg-white p-8">{children}</main>
    </div>
  )
}
