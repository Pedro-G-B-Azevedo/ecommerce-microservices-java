import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { cancelOrder, findOrder, type OrderResponse } from '../api/orders'
import { useAuth } from '../auth/AuthContext'
import { StatusBadge } from '../components/StatusBadge'
import { Money } from '../components/Money'
import { ErrorMessage } from '../components/ErrorMessage'

// A saga confirma ou rejeita o pedido de forma assíncrona; um curto polling
// poupa o cliente de precisar atualizar a página manualmente.
const POLL_INTERVAL_MS = 3000

export function OrderDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { session } = useAuth()
  const [order, setOrder] = useState<OrderResponse | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [cancelling, setCancelling] = useState(false)
  // O polling dispara requisições que podem responder fora de ordem; sem isto,
  // uma resposta antiga (ainda PENDING) pode chegar depois de uma mais nova e
  // apagar o status já confirmado ou rejeitado na tela.
  const requestSeq = useRef(0)

  const load = useCallback(() => {
    if (!session || !id) return
    const seq = ++requestSeq.current
    findOrder(id, session.token)
      .then((response) => {
        if (seq === requestSeq.current) setOrder(response)
      })
      .catch((err) => {
        if (seq === requestSeq.current) setError(err)
      })
  }, [session, id])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    if (order?.status !== 'PENDING') return
    const timer = setInterval(load, POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [order?.status, load])

  async function handleCancel() {
    if (!session || !id) return
    setCancelling(true)
    setError(null)
    try {
      setOrder(await cancelOrder(id, session.token))
    } catch (err) {
      setError(err)
    } finally {
      setCancelling(false)
    }
  }

  if (error) return <ErrorMessage error={error} />
  if (!order) return <p>Carregando...</p>

  return (
    <section>
      <h1>Pedido {order.id.slice(0, 8)}</h1>
      <p>
        <StatusBadge status={order.status} />
        {order.status === 'PENDING' && <span className="order-detail__waiting"> — aguardando confirmação de estoque</span>}
      </p>
      {order.rejectionReason && <p className="error-message">{order.rejectionReason}</p>}
      <table className="cart-table">
        <thead>
          <tr>
            <th>Produto</th>
            <th>Quantidade</th>
            <th>Preço unitário</th>
            <th>Subtotal</th>
          </tr>
        </thead>
        <tbody>
          {order.items.map((item) => (
            <tr key={item.productId}>
              <td>{item.productId.slice(0, 8)}</td>
              <td>{item.quantity}</td>
              <td>
                <Money value={item.unitPrice} />
              </td>
              <td>
                <Money value={item.subtotal} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="cart-total">
        Total: <Money value={order.totalAmount} />
      </p>
      {order.status === 'PENDING' && (
        <button type="button" onClick={handleCancel} disabled={cancelling}>
          {cancelling ? 'Cancelando...' : 'Cancelar pedido'}
        </button>
      )}
    </section>
  )
}
