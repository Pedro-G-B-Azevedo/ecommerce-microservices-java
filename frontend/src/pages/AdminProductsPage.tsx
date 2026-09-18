import { useState, type FormEvent } from 'react'
import { createProduct, replenishStock } from '../api/catalog'
import { useAuth } from '../auth/AuthContext'
import { ErrorMessage } from '../components/ErrorMessage'

export function AdminProductsPage() {
  const { session } = useAuth()
  return (
    <section>
      <h1>Administração do catálogo</h1>
      <div className="admin-grid">
        <CreateProductForm token={session!.token} />
        <ReplenishStockForm token={session!.token} />
      </div>
    </section>
  )
}

function CreateProductForm({ token }: { token: string }) {
  const [sku, setSku] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [price, setPrice] = useState('')
  const [initialQuantity, setInitialQuantity] = useState('0')
  const [error, setError] = useState<unknown>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setSuccess(null)
    try {
      const product = await createProduct(
        { sku, name, description: description || undefined, price, initialQuantity: Number(initialQuantity) },
        token,
      )
      setSuccess(`Produto "${product.name}" cadastrado.`)
      setSku('')
      setName('')
      setDescription('')
      setPrice('')
      setInitialQuantity('0')
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="admin-form">
      <h2>Novo produto</h2>
      <label>
        SKU
        <input required value={sku} onChange={(event) => setSku(event.target.value)} />
      </label>
      <label>
        Nome
        <input required value={name} onChange={(event) => setName(event.target.value)} />
      </label>
      <label>
        Descrição
        <input value={description} onChange={(event) => setDescription(event.target.value)} />
      </label>
      <label>
        Preço
        <input required type="number" step="0.01" min="0" value={price} onChange={(event) => setPrice(event.target.value)} />
      </label>
      <label>
        Quantidade inicial
        <input
          required
          type="number"
          min="0"
          value={initialQuantity}
          onChange={(event) => setInitialQuantity(event.target.value)}
        />
      </label>
      <ErrorMessage error={error} />
      {success && <p className="success-message">{success}</p>}
      <button type="submit" disabled={submitting}>
        {submitting ? 'Cadastrando...' : 'Cadastrar produto'}
      </button>
    </form>
  )
}

function ReplenishStockForm({ token }: { token: string }) {
  const [productId, setProductId] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [error, setError] = useState<unknown>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setSuccess(null)
    try {
      const stock = await replenishStock(productId, Number(quantity), token)
      setSuccess(`Estoque atualizado: ${stock.availableQuantity} unidades disponíveis.`)
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="admin-form">
      <h2>Entrada de estoque</h2>
      <label>
        Id do produto
        <input required value={productId} onChange={(event) => setProductId(event.target.value)} />
      </label>
      <label>
        Quantidade
        <input required type="number" min="1" value={quantity} onChange={(event) => setQuantity(event.target.value)} />
      </label>
      <ErrorMessage error={error} />
      {success && <p className="success-message">{success}</p>}
      <button type="submit" disabled={submitting}>
        {submitting ? 'Enviando...' : 'Dar entrada'}
      </button>
    </form>
  )
}
