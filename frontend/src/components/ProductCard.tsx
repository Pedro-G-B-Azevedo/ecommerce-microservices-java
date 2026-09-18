import { useState } from 'react'
import type { ProductResponse } from '../api/catalog'
import { useCart } from '../cart/CartContext'
import { Money } from './Money'

export function ProductCard({ product }: { product: ProductResponse }) {
  const { addItem } = useCart()
  const [quantity, setQuantity] = useState(1)
  const [added, setAdded] = useState(false)

  function handleAdd() {
    addItem(product, quantity)
    setAdded(true)
    setTimeout(() => setAdded(false), 1500)
  }

  return (
    <article className="product-card">
      <h3>{product.name}</h3>
      <p className="product-card__sku">{product.sku}</p>
      {product.description && <p className="product-card__description">{product.description}</p>}
      <p className="product-card__price">
        <Money value={product.price} />
      </p>
      <div className="product-card__actions">
        <input
          type="number"
          min={1}
          value={quantity}
          onChange={(event) => setQuantity(Math.max(1, Number(event.target.value)))}
          aria-label={`Quantidade de ${product.name}`}
        />
        <button type="button" onClick={handleAdd}>
          {added ? 'Adicionado!' : 'Adicionar ao carrinho'}
        </button>
      </div>
    </article>
  )
}
