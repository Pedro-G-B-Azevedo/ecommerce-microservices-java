import { useState, type FormEvent } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ErrorMessage } from '../components/ErrorMessage'

export function RegisterPage() {
  const { register, login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await register(email, password, fullName)
      // O cadastro não devolve token: entra em seguida, já autenticado.
      await login(email, password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-form">
      <h1>Cadastrar</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Nome completo
          <input required value={fullName} onChange={(event) => setFullName(event.target.value)} />
        </label>
        <label>
          E-mail
          <input type="email" required value={email} onChange={(event) => setEmail(event.target.value)} />
        </label>
        <label>
          Senha
          <input
            type="password"
            required
            minLength={12}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <small>Mínimo de 12 caracteres.</small>
        </label>
        <ErrorMessage error={error} />
        <button type="submit" disabled={submitting}>
          {submitting ? 'Cadastrando...' : 'Cadastrar'}
        </button>
      </form>
      <p>
        Já tem conta? <Link to="/entrar">Entre</Link>
      </p>
    </section>
  )
}
