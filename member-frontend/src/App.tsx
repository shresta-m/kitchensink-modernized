import type { ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { AddMemberPage } from './pages/AddMemberPage';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { LoginPage } from './pages/LoginPage';
import { MembersPage } from './pages/MembersPage';
import { RegisterPage } from './pages/RegisterPage';

function RequireAuth({ children, admin }: { children: ReactNode; admin?: boolean }) {
  const { user, loading } = useAuth();
  if (loading) return <div className="p-10 text-gray-600">Loading…</div>;
  if (!user) return <Navigate to="/login" replace />;
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />;
  if (admin && user.role !== 'ADMIN') return <Navigate to="/members" replace />;
  return <>{children}</>;
}

function RequireSession({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <div className="p-10 text-gray-600">Loading…</div>;
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/change-password" element={<RequireSession><ChangePasswordPage /></RequireSession>} />
        <Route path="/members" element={<RequireAuth><MembersPage /></RequireAuth>} />
        <Route path="/members/new" element={<RequireAuth admin><AddMemberPage /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/members" replace />} />
    </Routes>
  );
}
