import { API } from './config'
import { apiFetch, type PageResponse } from './http'

export interface ProductResponse {
  id: string
  sku: string
  name: string
  description: string | null
  price: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface StockResponse {
  productId: string
  availableQuantity: number
  reservedQuantity: number
  updatedAt: string
}

export interface CreateProductRequest {
  sku: string
  name: string
  description?: string
  price: string
  initialQuantity: number
}

export function searchProducts(term: string, page: number, token?: string | null): Promise<PageResponse<ProductResponse>> {
  const params = new URLSearchParams({ page: String(page), size: '12' })
  if (term) params.set('term', term)
  return apiFetch<PageResponse<ProductResponse>>(API.inventory, `/api/v1/products?${params}`, { token })
}

export function findStock(productId: string): Promise<StockResponse> {
  return apiFetch<StockResponse>(API.inventory, `/api/v1/products/${productId}/stock`)
}

export function createProduct(request: CreateProductRequest, token: string): Promise<ProductResponse> {
  return apiFetch<ProductResponse>(API.inventory, '/api/v1/products', {
    method: 'POST',
    body: request,
    token,
  })
}

export function replenishStock(productId: string, quantity: number, token: string): Promise<StockResponse> {
  return apiFetch<StockResponse>(API.inventory, `/api/v1/products/${productId}/stock/replenish`, {
    method: 'POST',
    body: { quantity },
    token,
  })
}
