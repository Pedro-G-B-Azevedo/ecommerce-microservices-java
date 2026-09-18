import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <section>
      <h1>Página não encontrada</h1>
      <p>
        <Link to="/">Voltar ao catálogo</Link>
      </p>
    </section>
  )
}
