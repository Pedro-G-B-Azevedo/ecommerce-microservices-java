import { createContext, use, useCallback, useMemo, useState, type ReactNode } from 'react'
import type { ProductResponse } from '../api/catalog'

export interface CartLine {
  productId: string
  name: string
  unitPrice: string
  quantity: number
}

interface CartContextValue {
  lines: CartLine[]
  addItem: (product: ProductResponse, quantity: number) => void
  setQuantity: (productId: string, quantity: number) => void
  removeItem: (productId: string) => void
  clear: () => void
  total: number
}

const CartContext = createContext<CartContextValue | null>(null)

const STORAGE_KEY = 'ecommerce.cart'

function loadLines(): CartLine[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as CartLine[]) : []
  } catch {
    return []
  }
}

function persist(lines: CartLine[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(lines))
}

export function CartProvider({ children }: { children: ReactNode }) {
  const [lines, setLines] = useState<CartLine[]>(() => loadLines())

  const addItem = useCallback((product: ProductResponse, quantity: number) => {
    setLines((current) => {
      const existing = current.find((line) => line.productId === product.id)
      const next = existing
        ? current.map((line) =>
            line.productId === product.id ? { ...line, quantity: line.quantity + quantity } : line,
          )
        : [...current, { productId: product.id, name: product.name, unitPrice: product.price, quantity }]
      persist(next)
      return next
    })
  }, [])

  const setQuantity = useCallback((productId: string, quantity: number) => {
    setLines((current) => {
      const next =
        quantity <= 0
          ? current.filter((line) => line.productId !== productId)
          : current.map((line) => (line.productId === productId ? { ...line, quantity } : line))
      persist(next)
      return next
    })
  }, [])

  const removeItem = useCallback((productId: string) => {
    setLines((current) => {
      const next = current.filter((line) => line.productId !== productId)
      persist(next)
      return next
    })
  }, [])

  const clear = useCallback(() => {
    persist([])
    setLines([])
  }, [])

  const total = useMemo(
    () => lines.reduce((sum, line) => sum + Number(line.unitPrice) * line.quantity, 0),
    [lines],
  )

  const value = useMemo(
    () => ({ lines, addItem, setQuantity, removeItem, clear, total }),
    [lines, addItem, setQuantity, removeItem, clear, total],
  )

  return <CartContext value={value}>{children}</CartContext>
}

export function useCart(): CartContextValue {
  const context = use(CartContext)
  if (!context) throw new Error('useCart precisa estar dentro de um CartProvider')
  return context
}
