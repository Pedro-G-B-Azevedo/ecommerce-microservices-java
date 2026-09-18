import { createContext, use, useCallback, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import type { Role } from '../api/auth'

interface Session {
  token: string
  email: string
  roles: Role[]
  expiresAt: string
}

interface AuthContextValue {
  session: Session | null
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, fullName: string) => Promise<void>
  logout: () => void
  hasRole: (role: Role) => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

const STORAGE_KEY = 'ecommerce.session'

function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    // Sessão vencida não serve de nada: melhor tratar como se não existisse.
    if (new Date(session.expiresAt).getTime() <= Date.now()) {
      localStorage.removeItem(STORAGE_KEY)
      return null
    }
    return session
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => loadSession())

  const login = useCallback(async (email: string, password: string) => {
    const token = await authApi.login(email, password)
    const next: Session = { token: token.accessToken, email, roles: token.roles, expiresAt: token.expiresAt }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    setSession(next)
  }, [])

  const register = useCallback(async (email: string, password: string, fullName: string) => {
    await authApi.register(email, password, fullName)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY)
    setSession(null)
  }, [])

  const hasRole = useCallback((role: Role) => session?.roles.includes(role) ?? false, [session])

  const value = useMemo(
    () => ({ session, login, register, logout, hasRole }),
    [session, login, register, logout, hasRole],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}

export function useAuth(): AuthContextValue {
  const context = use(AuthContext)
  if (!context) throw new Error('useAuth precisa estar dentro de um AuthProvider')
  return context
}
