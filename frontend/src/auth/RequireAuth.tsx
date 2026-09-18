import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import type { Role } from '../api/auth'

export function RequireAuth({ children, role }: { children: ReactNode; role?: Role }) {
  const { session, hasRole } = useAuth()
  const location = useLocation()

  if (!session) {
    return <Navigate to="/entrar" replace state={{ from: location.pathname }} />
  }
  if (role && !hasRole(role)) {
    return <Navigate to="/" replace />
  }
  return children
}
