import type { OrderStatus } from '../api/orders'

const LABELS: Record<OrderStatus, string> = {
  PENDING: 'Pendente',
  CONFIRMED: 'Confirmado',
  REJECTED: 'Rejeitado',
  CANCELLED: 'Cancelado',
}

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`badge badge--${status.toLowerCase()}`}>{LABELS[status]}</span>
}
