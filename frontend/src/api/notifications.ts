import { API } from './config'
import { apiFetch, type PageResponse } from './http'

export type NotificationType = 'ORDER_RECEIVED' | 'ORDER_CONFIRMED' | 'ORDER_REJECTED'
export type NotificationStatus = 'SENT' | 'FAILED'

export interface NotificationResponse {
  id: string
  orderId: string
  customerId: string
  type: NotificationType
  channel: 'EMAIL'
  recipient: string
  subject: string
  body: string
  status: NotificationStatus
  failureReason: string | null
  createdAt: string
  sentAt: string | null
}

export function searchNotifications(
  page: number,
  token: string,
  filters: { orderId?: string } = {},
): Promise<PageResponse<NotificationResponse>> {
  const params = new URLSearchParams({ page: String(page), size: '20' })
  if (filters.orderId) params.set('orderId', filters.orderId)
  return apiFetch<PageResponse<NotificationResponse>>(API.notifications, `/api/v1/notifications?${params}`, { token })
}
