import { useEffect, useState } from 'react'
import { searchProducts, type ProductResponse } from '../api/catalog'
import { ProductCard } from '../components/ProductCard'
import { ErrorMessage } from '../components/ErrorMessage'

export function CatalogPage() {
  const [term, setTerm] = useState('')
  const [page, setPage] = useState(0)
  const [products, setProducts] = useState<ProductResponse[]>([])
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    searchProducts(term, page)
      .then((response) => {
        if (cancelled) return
        setProducts(response.content)
        setTotalPages(response.totalPages)
      })
      .catch((err) => !cancelled && setError(err))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [term, page])

  return (
    <section>
      <h1>Catálogo</h1>
      <input
        type="search"
        placeholder="Buscar por nome ou SKU"
        value={term}
        onChange={(event) => {
          setPage(0)
          setTerm(event.target.value)
        }}
        className="catalog__search"
      />
      <ErrorMessage error={error} />
      {loading ? (
        <p>Carregando...</p>
      ) : products.length === 0 ? (
        <p>Nenhum produto encontrado.</p>
      ) : (
        <div className="product-grid">
          {products.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
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
