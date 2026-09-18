import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCart } from '../cart/CartContext'
import { useAuth } from '../auth/AuthContext'
import { createOrder } from '../api/orders'
import { Money } from '../components/Money'
import { ErrorMessage } from '../components/ErrorMessage'

export function CartPage() {
  const { lines, setQuantity, removeItem, clear, total } = useCart()
  const { session } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<unknown>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleCheckout() {
    if (!session) {
      navigate('/entrar', { state: { from: '/carrinho' } })
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const order = await createOrder(
        { items: lines.map((line) => ({ productId: line.productId, quantity: line.quantity })) },
        session.token,
      )
      clear()
      navigate(`/pedidos/${order.id}`)
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  if (lines.length === 0) {
    return (
      <section>
        <h1>Carrinho</h1>
        <p>O carrinho está vazio.</p>
      </section>
    )
  }

  return (
    <section>
      <h1>Carrinho</h1>
      <table className="cart-table">
        <thead>
          <tr>
            <th>Produto</th>
            <th>Preço unitário</th>
            <th>Quantidade</th>
            <th>Subtotal</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {lines.map((line) => (
            <tr key={line.productId}>
              <td>{line.name}</td>
              <td>
                <Money value={line.unitPrice} />
              </td>
              <td>
                <input
                  type="number"
                  min={1}
                  value={line.quantity}
                  onChange={(event) => setQuantity(line.productId, Number(event.target.value))}
                  aria-label={`Quantidade de ${line.name}`}
                />
              </td>
              <td>
                <Money value={Number(line.unitPrice) * line.quantity} />
              </td>
              <td>
                <button type="button" onClick={() => removeItem(line.productId)}>
                  Remover
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="cart-total">
        Total: <Money value={total} />
      </p>
      <ErrorMessage error={error} />
      <button type="button" onClick={handleCheckout} disabled={submitting}>
        {submitting ? 'Enviando...' : 'Finalizar pedido'}
      </button>
    </section>
  )
}
