import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { searchOrders, type OrderSummaryResponse } from '../api/orders'
import { useAuth } from '../auth/AuthContext'
import { StatusBadge } from '../components/StatusBadge'
import { Money } from '../components/Money'
import { ErrorMessage } from '../components/ErrorMessage'

export function OrdersPage() {
  const { session, hasRole } = useAuth()
  const [page, setPage] = useState(0)
  const [orders, setOrders] = useState<OrderSummaryResponse[]>([])
  const [totalPages, setTotalPages] = useState(1)
  const [customerId, setCustomerId] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (!session) return
    let cancelled = false
    setLoading(true)
    setError(null)
    searchOrders(page, session.token, hasRole('ADMIN') && customerId ? { customerId } : {})
      .then((response) => {
        if (cancelled) return
        setOrders(response.content)
        setTotalPages(response.totalPages)
      })
      .catch((err) => !cancelled && setError(err))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [session, page, customerId, hasRole])

  return (
    <section>
      <h1>{hasRole('ADMIN') ? 'Pedidos' : 'Meus pedidos'}</h1>
      {hasRole('ADMIN') && (
        <input
          placeholder="Filtrar por id do cliente"
          value={customerId}
          onChange={(event) => {
            setPage(0)
            setCustomerId(event.target.value)
          }}
        />
      )}
      <ErrorMessage error={error} />
      {loading ? (
        <p>Carregando...</p>
      ) : orders.length === 0 ? (
        <p>Nenhum pedido encontrado.</p>
      ) : (
        <table className="orders-table">
          <thead>
            <tr>
              <th>Pedido</th>
              <th>Status</th>
              <th>Total</th>
              <th>Criado em</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.id}>
                <td>
                  <Link to={`/pedidos/${order.id}`}>{order.id.slice(0, 8)}</Link>
                </td>
                <td>
                  <StatusBadge status={order.status} />
                </td>
                <td>
                  <Money value={order.totalAmount} />
                </td>
                <td>{new Date(order.createdAt).toLocaleString('pt-BR')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {totalPages > 1 && (
        <div className="pagination">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Anterior
          </button>
          <span>
            Página {page + 1} de {totalPages}
          </span>
          <button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
            Próxima
          </button>
        </div>
      )}
    </section>
  )
}
