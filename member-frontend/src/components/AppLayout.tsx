import type { ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Badge, Button } from './ui';

/** Header plus page container for signed-in screens. */
export function AppLayout({ children }: { children: ReactNode }) {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-white">
      <header className="border-b border-gray-200">
        <div className="mx-auto flex min-h-16 max-w-[1200px] flex-wrap items-center justify-between gap-3 px-8">
          <div className="flex items-center gap-8">
            <Link to="/members" className="flex items-center gap-2.5 no-underline">
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-teal-700 text-base font-bold text-white">K</span>
              <span className="text-[17px] font-semibold text-gray-900">Kitchensink</span>
            </Link>
            <nav aria-label="Main">
              <Link to="/members" className="rounded-lg bg-teal-50 px-3.5 py-2 text-sm font-semibold text-teal-900 no-underline">
                {user?.role === 'ADMIN' ? 'Members' : 'Member directory'}
              </Link>
            </nav>
          </div>
          <div className="flex items-center gap-3.5">
            <Badge tone="teal">{user?.role}</Badge>
            <span className="text-sm font-medium">{user?.name}</span>
            <Button
              variant="secondary"
              size="sm"
              onClick={async () => {
                await signOut();
                navigate('/login');
              }}
            >
              Sign out
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto flex max-w-[1200px] flex-col gap-6 px-8 py-8">{children}</main>
    </div>
  );
}
