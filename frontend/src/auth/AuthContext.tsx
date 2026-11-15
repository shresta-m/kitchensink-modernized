import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '../api/auth'
import { setUnauthorizedHandler, tokenStore } from '../api/http'
import type { Role } from '../types/api'

interface User {
  username: string
  role: Role
}

interface AuthState {
  user: User | null
  /** true while a stored token is being checked on first load */
  loading: boolean
  isAdmin: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  /** true when the last sign-out was caused by an expired or rejected token */
  sessionExpired: boolean
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState<boolean>(() => tokenStore.get() !== null)
  const [sessionExpired, setSessionExpired] = useState(false)

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
    queryClient.clear() // no member data may outlive the session
  }, [queryClient])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      if (tokenStore.get()) setSessionExpired(true)
      logout()
    })
  }, [logout])

  // Restore the session from a stored token.
  useEffect(() => {
    if (!tokenStore.get()) return
    authApi
      .me()
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const res = await authApi.login(username, password)
    tokenStore.set(res.accessToken)
    setSessionExpired(false)
    setUser(res.user)
  }, [])

  const value = useMemo<AuthState>(
    () => ({ user, loading, isAdmin: user?.role === 'ADMIN', login, logout, sessionExpired }),
    [user, loading, login, logout, sessionExpired],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
