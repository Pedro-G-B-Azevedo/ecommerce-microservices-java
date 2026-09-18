import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ErrorMessage } from '../components/ErrorMessage'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await login(email, password)
      const from = (location.state as { from?: string } | null)?.from ?? '/'
      navigate(from, { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-form">
      <h1>Entrar</h1>
      <form onSubmit={handleSubmit}>
        <label>
          E-mail
          <input type="email" required value={email} onChange={(event) => setEmail(event.target.value)} />
        </label>
        <label>
          Senha
          <input
            type="password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>
        <ErrorMessage error={error} />
        <button type="submit" disabled={submitting}>
          {submitting ? 'Entrando...' : 'Entrar'}
        </button>
      </form>
      <p>
        Não tem conta? <Link to="/cadastrar">Cadastre-se</Link>
      </p>
    </section>
  )
}
