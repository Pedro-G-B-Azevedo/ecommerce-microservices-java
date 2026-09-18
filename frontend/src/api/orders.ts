import { API } from './config'
import { apiFetch, type PageResponse } from './http'

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED'

export interface OrderItemResponse {
  productId: string
  quantity: number
  unitPrice: string
  subtotal: string
}

export interface OrderResponse {
  id: string
  customerId: string
  status: OrderStatus
  totalAmount: string
  items: OrderItemResponse[]
  rejectionReason: string | null
  createdAt: string
  updatedAt: string
}

export interface OrderSummaryResponse {
  id: string
  customerId: string
  status: OrderStatus
  totalAmount: string
  createdAt: string
}

export interface CreateOrderRequest {
  items: { productId: string; quantity: number }[]
}

export function createOrder(request: CreateOrderRequest, token: string): Promise<OrderResponse> {
  return apiFetch<OrderResponse>(API.orders, '/api/v1/orders', {
    method: 'POST',
    body: request,
    token,
  })
}

export function findOrder(id: string, token: string): Promise<OrderResponse> {
  return apiFetch<OrderResponse>(API.orders, `/api/v1/orders/${id}`, { token })
}

export function searchOrders(
  page: number,
  token: string,
  filters: { customerId?: string; status?: OrderStatus } = {},
): Promise<PageResponse<OrderSummaryResponse>> {
  const params = new URLSearchParams({ page: String(page), size: '20' })
  if (filters.customerId) params.set('customerId', filters.customerId)
  if (filters.status) params.set('status', filters.status)
  return apiFetch<PageResponse<OrderSummaryResponse>>(API.orders, `/api/v1/orders?${params}`, { token })
}

export function cancelOrder(id: string, token: string): Promise<OrderResponse> {
  return apiFetch<OrderResponse>(API.orders, `/api/v1/orders/${id}/cancel`, {
    method: 'POST',
    token,
  })
}
