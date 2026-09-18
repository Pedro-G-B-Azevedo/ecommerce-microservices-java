/**
 * Cada serviço tem sua própria origem: não existe API gateway neste projeto,
 * então o navegador fala diretamente com cada um. As portas padrão são as
 * publicadas pelo docker-compose para uso local sem configuração.
 */
export const API = {
  auth: import.meta.env.VITE_AUTH_URL ?? 'http://localhost:8081',
  orders: import.meta.env.VITE_ORDERS_URL ?? 'http://localhost:8082',
  inventory: import.meta.env.VITE_INVENTORY_URL ?? 'http://localhost:8083',
  notifications: import.meta.env.VITE_NOTIFICATIONS_URL ?? 'http://localhost:8084',
}
