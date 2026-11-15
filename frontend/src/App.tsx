import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { ProtectedRoute } from './components/ProtectedRoute'
import { AdminRegisterPage } from './pages/AdminRegisterPage'
import { LoginPage } from './pages/LoginPage'
import { MembersPage } from './pages/MembersPage'
import { NewMemberPage } from './pages/NewMemberPage'
import { RegisterPage } from './pages/RegisterPage'
import { UsersPage } from './pages/UsersPage'
import { ADMIN_SIGNUP_PATH } from './routes'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path={ADMIN_SIGNUP_PATH} element={<AdminRegisterPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/members" element={<MembersPage />} />
          <Route element={<ProtectedRoute adminOnly />}>
            <Route path="/members/new" element={<NewMemberPage />} />
            <Route path="/admin/users" element={<UsersPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/members" replace />} />
    </Routes>
  )
}
