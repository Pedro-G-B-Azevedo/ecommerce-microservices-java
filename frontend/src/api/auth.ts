import { API } from './config'
import { apiFetch } from './http'

export type Role = 'CLIENTE' | 'ADMIN' | 'SERVICE'

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  expiresAt: string
  roles: Role[]
}

export interface UserResponse {
  id: string
  email: string
  fullName: string
  roles: Role[]
  enabled: boolean
  createdAt: string
}

export function login(email: string, password: string): Promise<TokenResponse> {
  return apiFetch<TokenResponse>(API.auth, '/api/v1/auth/login', {
    method: 'POST',
    body: { email, password },
  })
}

export function register(email: string, password: string, fullName: string): Promise<UserResponse> {
  return apiFetch<UserResponse>(API.auth, '/api/v1/auth/register', {
    method: 'POST',
    body: { email, password, fullName },
  })
}

export function me(token: string): Promise<UserResponse> {
  return apiFetch<UserResponse>(API.auth, '/api/v1/auth/me', { token })
}
