import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as api from './api';

const AuthContext = createContext(null);
function roleHint(token) {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload)).role;
  } catch { return undefined; }
}
export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('shopsphere.jwt'));
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(Boolean(token));
  const clear = useCallback(() => { localStorage.removeItem('shopsphere.jwt'); setToken(null); setUser(null); setLoading(false); }, []);
  useEffect(() => api.configureApi({ getToken: () => token, unauthorized: clear }), [token, clear]);
  useEffect(() => {
    if (!token) { setLoading(false); return; }
    let alive = true;
    api.getProfile().then((profile) => { if (alive) setUser({ ...profile, role: profile.role || roleHint(token) }); }).catch(() => { if (alive) clear(); }).finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, []); // bootstrap the saved session once
  const signIn = useCallback((result) => { localStorage.setItem('shopsphere.jwt', result.accessToken); setToken(result.accessToken); setUser(result.user); setLoading(false); }, []);
  const signOut = useCallback(async () => { try { if (token) await api.logout(); } finally { clear(); } }, [token, clear]);
  const value = useMemo(() => ({ token, user, loading, signIn, signOut, setUser, clear }), [token, user, loading, signIn, signOut, clear]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() { return useContext(AuthContext); }
