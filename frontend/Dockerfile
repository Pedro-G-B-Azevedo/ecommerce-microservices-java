# Etapa de build: compila o SPA. As URLs das APIs entram como build args porque
# o Vite resolve import.meta.env.VITE_* em tempo de build — o resultado é um
# bundle estático, sem servidor por trás, então essas URLs precisam já estar
# corretas quando o navegador de quem acessa carrega os arquivos.
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,id=npm-frontend,target=/root/.npm,sharing=locked \
    npm ci

COPY . .

# Padrão para as portas publicadas pelo docker-compose deste projeto: o
# navegador acessa cada serviço diretamente, não há API gateway.
ARG VITE_AUTH_URL=http://localhost:8081
ARG VITE_ORDERS_URL=http://localhost:8082
ARG VITE_INVENTORY_URL=http://localhost:8083
ARG VITE_NOTIFICATIONS_URL=http://localhost:8084

RUN npm run build

# Etapa de execução: só os arquivos estáticos. A imagem "unprivileged" do Nginx
# já roda como usuário sem privilégios e escuta em 8080, consistente com os
# outros serviços deste projeto, nenhum dos quais roda como root.
FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
COPY docker-entrypoint.d/40-render-port.sh /docker-entrypoint.d/40-render-port.sh
RUN chmod +x /docker-entrypoint.d/40-render-port.sh

EXPOSE 8080
