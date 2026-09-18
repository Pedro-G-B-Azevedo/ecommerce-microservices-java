# frontend

SPA em React 19 + TypeScript (Vite) para o
[ecommerce-microservices-java](../README.md). Veja a seção
[Front-end](../README.md#front-end) do README principal para a visão geral,
as decisões de arquitetura e como subir tudo junto via Docker Compose.

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # typecheck + bundle de produção
```

Por padrão fala com os quatro serviços em `localhost:8081`-`8084`; ajuste as
variáveis `VITE_AUTH_URL`, `VITE_ORDERS_URL`, `VITE_INVENTORY_URL` e
`VITE_NOTIFICATIONS_URL` (veja `src/api/config.ts`) se estiverem em outro
lugar.
