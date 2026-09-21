# Deploy gratuito (Aiven + Render)

Guia para colocar o projeto inteiro no ar, de graça e sem cartão de crédito,
numa URL que qualquer pessoa acessa. Usa dois provedores:

- **Aiven** — Postgres e Kafka gerenciados, tier gratuito permanente.
- **Render** — os cinco containers (quatro serviços Spring + o front-end),
  usando os `Dockerfile` que já existem no repositório.

Duas limitações para ter em mente, não são bugs, são do próprio tier gratuito:

- **Serviços do Render dormem após 15 minutos sem tráfego** e levam uns
  30-60s para acordar no primeiro acesso seguinte. Normal, só esperar.
- **O Kafka da Aiven desliga sozinho depois de 24h sem atividade** (nenhuma
  mensagem publicada/consumida) e **não acorda sozinho** — precisa entrar no
  console da Aiven e ligar de novo manualmente. **Antes de mostrar o projeto
  para alguém, confira se o serviço Kafka está "Running".**

## 1. Aiven — Postgres

1. [aiven.io](https://aiven.io) → criar conta grátis (dá pra usar login do
   GitHub) → sem necessidade de cartão.
2. **Create service** → PostgreSQL → plano **Free** → região próxima → aguardar
   ficar "Running".
3. Criar os 4 bancos usados pelo projeto. No console da Aiven, aba
   **Databases** do serviço (ou via `psql` com a connection string que a
   Aiven mostra):
   ```sql
   CREATE DATABASE auth_db;
   CREATE DATABASE orders_db;
   CREATE DATABASE inventory_db;
   CREATE DATABASE notification_db;
   ```
4. Anote da tela de conexão: **host**, **port**, **user** (geralmente
   `avnadmin`) e **password**. A URL de cada serviço vai ser:
   ```
   jdbc:postgresql://<host>:<port>/auth_db?sslmode=require
   jdbc:postgresql://<host>:<port>/orders_db?sslmode=require
   jdbc:postgresql://<host>:<port>/inventory_db?sslmode=require
   jdbc:postgresql://<host>:<port>/notification_db?sslmode=require
   ```
   O `?sslmode=require` é obrigatório — a Aiven não aceita conexão sem TLS.

## 2. Aiven — Kafka

1. **Create service** → Kafka → plano **Free**.
2. Nos tópicos do serviço, criar estes 4 (nomes exatos, usados literalmente
   no código):

   | Tópico | Partições | Fator de replicação |
   | --- | --- | --- |
   | `orders.order-created` | 1 | 1 (ou o padrão que a Aiven sugerir) |
   | `inventory.stock-reserved` | 1 | 1 |
   | `inventory.stock-rejected` | 1 | 1 |
   | `dead-letter-topic` | 1 | 1 |

   São 4 no total — o tier gratuito permite até 5. O `dead-letter-topic` é
   único e compartilhado pelos três serviços de propósito: o padrão do Spring
   Kafka seria um `.DLT` por tópico de origem, o que daria 6 tópicos e
   estouraria o limite.

3. Habilitar autenticação SASL: na configuração avançada do serviço, ativar
   `kafka_authentication_methods.sasl`. Depois, na aba de usuários/ACLs,
   pegar (ou criar) um usuário com permissão de leitura e escrita nesses 4
   tópicos — usuário e senha.
4. Baixar o certificado CA do serviço (arquivo `ca.pem` no console da Aiven).
   O conteúdo inteiro do arquivo (com as linhas `-----BEGIN CERTIFICATE-----`
   e `-----END CERTIFICATE-----`) é o valor de `KAFKA_SSL_TRUSTSTORE_CERTIFICATES`
   mais adiante.
5. Anote o **bootstrap server** (formato `host:porta`).
6. Monte o valor de `KAFKA_SASL_JAAS_CONFIG` substituindo usuário e senha:
   ```
   org.apache.kafka.common.security.scram.ScramLoginModule required username="SEU_USUARIO" password="SUA_SENHA";
   ```
   (o `;` no final faz parte da sintaxe, não é pontuação da frase)

## 3. Gerar o par de chaves JWT

O `auth-service` assina os tokens com uma chave RSA. Sem uma fixa, ele gera
uma nova a cada reinício e invalida todas as sessões — inaceitável num
serviço que hiberna e acorda o tempo todo. Gere uma vez, na sua máquina:

```bash
openssl genrsa -out private.pem 2048
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in private.pem -out private_pkcs8.pem
openssl rsa -in private.pem -pubout -out public.pem
```

O conteúdo de `private_pkcs8.pem` vai em `JWT_PRIVATE_KEY`, e o de
`public.pem` em `JWT_PUBLIC_KEY` (só no `ecommerce-auth-service`; os outros
três buscam a chave pública sozinhos, em tempo de execução, pelo endpoint
JWKS). Apague os três arquivos `.pem` depois de colar os valores — são a
chave privada do seu deploy.

## 4. Render — subir os 5 serviços

1. [render.com](https://render.com) → criar conta (dá pra usar login do
   GitHub, o que já resolve a conexão) → sem cartão para o tier gratuito.
2. **New** → **Blueprint** → aponte para o repositório no GitHub. O Render lê
   o `render.yaml` da raiz e propõe criar os cinco serviços definidos nele:
   `ecommerce-auth-service`, `ecommerce-orders-service`,
   `ecommerce-inventory-service`, `ecommerce-notification-service` e
   `ecommerce-frontend`.

   > Se algum desses nomes já estiver em uso por outra pessoa no Render, ele
   > vai sugerir um nome diferente para aquele serviço. Se isso acontecer,
   > anote a URL real que o Render atribuiu e ajuste manualmente as
   > variáveis de ambiente que referenciam esse serviço nos outros (o
   > `render.yaml` cruza as URLs entre si — `JWT_JWK_SET_URI`,
   > `INVENTORY_BASE_URL`, `AUTH_BASE_URL`, `CORS_ALLOWED_ORIGINS` e as
   > quatro `VITE_*` do front-end).

3. Aceitar e criar o blueprint. Os cinco serviços começam a buildar, mas vão
   falhar ao subir — faltam as variáveis marcadas `sync: false` no
   `render.yaml`, que só você pode preencher.

   > O blueprint já define `DB_POOL_MAX_SIZE=2` para os quatro serviços
   > Spring — não precisa preencher isso. É necessário porque o Postgres
   > gratuito da Aiven tem um teto de 20 conexões e não tem pooling
   > (PgBouncer) por cima; o padrão do Hikari é 10 conexões por serviço, o
   > que sozinho já estouraria o limite com dois serviços de pé.

4. Para cada serviço, na aba **Environment**, colar os valores:

   **ecommerce-auth-service**
   - `AUTH_DB_URL`, `AUTH_DB_USER`, `AUTH_DB_PASSWORD` → da Aiven (banco `auth_db`)
   - `AUTH_ADMIN_PASSWORD` → escolha uma senha (é com ela que você entra como admin)
   - `AUTH_SERVICE_ACCOUNT_PASSWORD` → escolha uma senha (anote: vai repetir no orders-service)
   - `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` → do passo 3

   **ecommerce-orders-service**
   - `ORDERS_DB_URL`, `ORDERS_DB_USER`, `ORDERS_DB_PASSWORD` → da Aiven (banco `orders_db`)
   - `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_SASL_JAAS_CONFIG`, `KAFKA_SSL_TRUSTSTORE_CERTIFICATES` → da Aiven
   - `SERVICE_ACCOUNT_PASSWORD` → a MESMA senha que você colocou em `AUTH_SERVICE_ACCOUNT_PASSWORD`

   **ecommerce-inventory-service**
   - `INVENTORY_DB_URL`, `INVENTORY_DB_USER`, `INVENTORY_DB_PASSWORD` → da Aiven (banco `inventory_db`)
   - `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_SASL_JAAS_CONFIG`, `KAFKA_SSL_TRUSTSTORE_CERTIFICATES` → da Aiven

   **ecommerce-notification-service**
   - `NOTIFICATION_DB_URL`, `NOTIFICATION_DB_USER`, `NOTIFICATION_DB_PASSWORD` → da Aiven (banco `notification_db`)
   - `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_SASL_JAAS_CONFIG`, `KAFKA_SSL_TRUSTSTORE_CERTIFICATES` → da Aiven

   **ecommerce-frontend**
   - Nada a preencher — as quatro `VITE_*` já vêm prontas no blueprint.

5. Depois de preencher, cada serviço reinicia sozinho (ou clique em **Manual
   Deploy** se não reiniciar). Acompanhe os logs de cada um até aparecer
   `Started ...Application` — o mesmo texto que aparece quando roda local.

## Problemas comuns

**`FATAL: remaining connection slots are reserved for roles with the
SUPERUSER attribute`** — o Postgres gratuito da Aiven estourou o limite de
20 conexões. Confira se `DB_POOL_MAX_SIZE=2` está mesmo definido nos quatro
serviços (o blueprint já define isso, mas confira na aba Environment de
cada um se você editou algo manualmente). Se estiver definido em todos e o
erro persistir, é provável que sobrem conexões "zumbis" de deploys
anteriores que falharam de forma abrupta — reinicie o serviço Postgres no
console da Aiven (não apaga dados, só derruba conexões) e tente o deploy
de novo.

**`Login module control flag is not available in the JAAS config`** — o
valor de `KAFKA_SASL_JAAS_CONFIG` está incompleto ou quebrado. Ele precisa
ser exatamente uma linha, neste formato, com a palavra `required` e o `;`
no final:
```
org.apache.kafka.common.security.scram.ScramLoginModule required username="SEU_USUARIO" password="SUA_SENHA";
```
Erros comuns ao colar: esquecer o `required`, esquecer o `;` final, ou uma
quebra de linha entrando no meio do texto. Cole de novo, com cuidado, nos
três serviços que usam Kafka (`ecommerce-orders-service`,
`ecommerce-inventory-service`, `ecommerce-notification-service`).

## 5. Testar

Abra `https://ecommerce-frontend.onrender.com` (ou a URL que o Render deu ao
serviço do front-end). O primeiro acesso pode demorar — os serviços estavam
dormindo. Entre com a conta admin (`admin@ecommerce.local` e a senha do passo
4), cadastre um produto no painel admin, depois abra uma aba anônima,
cadastre-se como cliente e faça um pedido — a mesma jornada que já foi
verificada localmente durante o desenvolvimento.

## Antes de mostrar para alguém

- Acesse o front-end alguns minutos antes, pra os serviços já estarem
  acordados quando a outra pessoa olhar.
- Confira no console da Aiven se o serviço **Kafka está "Running"** — se
  passou mais de 24h sem uso, ele desligou sozinho e precisa ser ligado ali
  manualmente antes da demonstração.
