import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const API_ORIGIN = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '')

export function Layout() {
  const { user, logout, isAdmin } = useAuth()
  const navClass = ({ isActive }: { isActive: boolean }) =>
    `rounded-lg px-3.5 py-2 text-sm no-underline ${isActive ? 'bg-brand-tint font-semibold text-brand-dark' : 'font-medium text-ink-soft hover:bg-surface'}`

  return (
    <div className="min-h-screen bg-white">
      <header className="border-b border-line bg-white">
        <div className="mx-auto flex min-h-16 max-w-[1200px] flex-wrap items-center justify-between gap-3 px-8">
          <div className="flex items-center gap-8">
            <Link to="/members" className="flex items-center gap-2.5 text-ink no-underline">
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand text-base font-bold text-white">K</span>
              <span className="text-[17px] font-semibold">Kitchensink</span>
            </Link>
            <nav className="flex gap-1">
              <NavLink to="/members" className={navClass}>Members</NavLink>
              {isAdmin && <NavLink to="/admin/users" className={navClass}>Users</NavLink>}
              {isAdmin && (
                <a href={`${API_ORIGIN}/swagger-ui.html`} target="_blank" rel="noreferrer"
                  className="rounded-lg px-3.5 py-2 text-sm font-medium text-ink-soft no-underline hover:bg-surface">
                  API docs
                </a>
              )}
            </nav>
          </div>
          <div className="flex items-center gap-3.5">
            <span className="rounded-full bg-brand-tint px-2.5 py-1 text-xs font-semibold text-brand-dark">{user?.role}</span>
            <span className="text-sm font-medium">{user?.username}</span>
            <button type="button" onClick={logout} className="btn-neutral h-9 px-3.5">Sign out</button>
          </div>
        </div>
      </header>
      <main className="mx-auto flex max-w-[1200px] flex-col gap-6 px-8 py-8">
        <Outlet />
      </main>
    </div>
  )
}
