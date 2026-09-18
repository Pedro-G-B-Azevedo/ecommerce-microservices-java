import { useEffect, useState } from 'react'
import { searchNotifications, type NotificationResponse } from '../api/notifications'
import { useAuth } from '../auth/AuthContext'
import { ErrorMessage } from '../components/ErrorMessage'

const TYPE_LABELS: Record<NotificationResponse['type'], string> = {
  ORDER_RECEIVED: 'Pedido recebido',
  ORDER_CONFIRMED: 'Pedido confirmado',
  ORDER_REJECTED: 'Pedido rejeitado',
}

export function NotificationsPage() {
  const { session } = useAuth()
  const [page, setPage] = useState(0)
  const [notifications, setNotifications] = useState<NotificationResponse[]>([])
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (!session) return
    let cancelled = false
    setLoading(true)
    searchNotifications(page, session.token)
      .then((response) => {
        if (cancelled) return
        setNotifications(response.content)
        setTotalPages(response.totalPages)
      })
      .catch((err) => !cancelled && setError(err))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [session, page])

  return (
    <section>
      <h1>Notificações</h1>
      <ErrorMessage error={error} />
      {loading ? (
        <p>Carregando...</p>
      ) : notifications.length === 0 ? (
        <p>Nenhuma notificação ainda.</p>
      ) : (
        <ul className="notification-list">
          {notifications.map((notification) => (
            <li key={notification.id} className={`notification notification--${notification.status.toLowerCase()}`}>
              <strong>{TYPE_LABELS[notification.type]}</strong>
              <span className="notification__subject">{notification.subject}</span>
              <p>{notification.body}</p>
              <time>{new Date(notification.createdAt).toLocaleString('pt-BR')}</time>
            </li>
          ))}
        </ul>
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
