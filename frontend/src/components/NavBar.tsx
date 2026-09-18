import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useCart } from '../cart/CartContext'

export function NavBar() {
  const { session, logout, hasRole } = useAuth()
  const { lines } = useCart()
  const itemCount = lines.reduce((sum, line) => sum + line.quantity, 0)

  return (
    <header className="navbar">
      <NavLink to="/" className="navbar__brand">
        e-commerce
      </NavLink>
      <nav className="navbar__links">
        <NavLink to="/">Catálogo</NavLink>
        <NavLink to="/carrinho">Carrinho{itemCount > 0 ? ` (${itemCount})` : ''}</NavLink>
        {session && <NavLink to="/pedidos">Meus pedidos</NavLink>}
        {session && <NavLink to="/notificacoes">Notificações</NavLink>}
        {hasRole('ADMIN') && <NavLink to="/admin/produtos">Admin</NavLink>}
      </nav>
      <div className="navbar__session">
        {session ? (
          <>
            <span className="navbar__email">{session.email}</span>
            <button type="button" onClick={logout}>
              Sair
            </button>
          </>
        ) : (
          <>
            <NavLink to="/entrar">Entrar</NavLink>
            <NavLink to="/cadastrar">Cadastrar</NavLink>
          </>
        )}
      </div>
    </header>
  )
}
