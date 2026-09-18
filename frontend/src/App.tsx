import { Route, Routes } from 'react-router-dom'
import { NavBar } from './components/NavBar'
import { RequireAuth } from './auth/RequireAuth'
import { CatalogPage } from './pages/CatalogPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { CartPage } from './pages/CartPage'
import { OrdersPage } from './pages/OrdersPage'
import { OrderDetailPage } from './pages/OrderDetailPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { AdminProductsPage } from './pages/AdminProductsPage'
import { NotFoundPage } from './pages/NotFoundPage'

export default function App() {
  return (
    <>
      <NavBar />
      <main className="content">
        <Routes>
          <Route path="/" element={<CatalogPage />} />
          <Route path="/entrar" element={<LoginPage />} />
          <Route path="/cadastrar" element={<RegisterPage />} />
          <Route path="/carrinho" element={<CartPage />} />
          <Route
            path="/pedidos"
            element={
              <RequireAuth>
                <OrdersPage />
              </RequireAuth>
            }
          />
          <Route
            path="/pedidos/:id"
            element={
              <RequireAuth>
                <OrderDetailPage />
              </RequireAuth>
            }
          />
          <Route
            path="/notificacoes"
            element={
              <RequireAuth>
                <NotificationsPage />
              </RequireAuth>
            }
          />
          <Route
            path="/admin/produtos"
            element={
              <RequireAuth role="ADMIN">
                <AdminProductsPage />
              </RequireAuth>
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
    </>
  )
}
