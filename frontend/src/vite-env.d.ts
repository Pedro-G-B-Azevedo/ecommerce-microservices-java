/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_AUTH_URL?: string
  readonly VITE_ORDERS_URL?: string
  readonly VITE_INVENTORY_URL?: string
  readonly VITE_NOTIFICATIONS_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
