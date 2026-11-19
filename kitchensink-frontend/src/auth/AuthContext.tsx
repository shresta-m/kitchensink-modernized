import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi, toCurrentUser } from '../api/auth';
import { setUnauthorizedHandler, tokenStore } from '../api/client';
import type { CurrentUser, LoginResponse } from '../api/types';

interface AuthState {
  user: CurrentUser | null;
  loading: boolean;
  signIn: (res: LoginResponse) => void;
  /** Revokes every session on the server, then clears this browser's copy. Never throws: leaving always works. */
  signOut: () => Promise<void>;
  /** Forgets the session locally only. For a token the server already rejected, or after a password change. */
  clearSession: () => void;
  /** Re-reads the account from the server. */
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading] = useState(false); // no session restore: the token is memory-only

  const clearSession = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  const signOut = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // Offline, or the token was already rejected: the local session still has to end.
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const signIn = useCallback((res: LoginResponse) => {
    tokenStore.set(res.accessToken);
    setUser(toCurrentUser(res.member, res.mustChangePassword));
  }, []);

  const refresh = useCallback(async () => {
    const me = await authApi.me();
    setUser(me ?? null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(clearSession);
    return () => setUnauthorizedHandler(null);
  }, [clearSession]);


  const value = useMemo(
    () => ({ user, loading, signIn, signOut, clearSession, refresh }),
    [user, loading, signIn, signOut, clearSession, refresh],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
