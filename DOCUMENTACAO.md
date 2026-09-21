# Documentação técnica — ecommerce-microservices-java

> Este documento foi escrito a partir de uma leitura direta do código-fonte do
> repositório, não do plano original do projeto. Onde alguma coisa planejada
> não foi implementada (ou foi implementada de forma diferente do previsto),
> isso está dito explicitamente, não omitido.

## 1. Visão geral

Este é um sistema de e-commerce simplificado construído como um conjunto de
microsserviços independentes em Java/Spring Boot, com um front-end em React.
O domínio é deliberadamente pequeno — catálogo, pedido, estoque, notificação
— porque o que o projeto se propõe a demonstrar não é a complexidade do
negócio, e sim um conjunto de decisões de engenharia que aparecem em sistemas
distribuídos de verdade: comunicação síncrona e assíncrona entre serviços,
consistência eventual, idempotência, autenticação sem estado compartilhado,
observabilidade e um pipeline de CI/CD com gates reais.

O sistema tem quatro serviços de backend, cada um dono do seu próprio banco
PostgreSQL (nenhum serviço lê a tabela de outro), mais um front-end React que
fala diretamente com os quatro. O fluxo central é a criação de um pedido: o
`orders-service` recebe a requisição, mas não decide sozinho se o pedido pode
ser atendido — ele publica um evento no Kafka, o `inventory-service` tenta
reservar o estoque de forma independente, e o resultado volta por outro
evento. É uma saga coreografada: não existe um orquestrador central dizendo
"agora faça isso, agora faça aquilo"; cada serviço reage aos eventos que lhe
interessam e publica os seus.

Duas coisas tornam este projeto diferente de um CRUD com um `docker-compose`:
primeiro, ele foi construído em etapas incrementais reais, com testes contra
infraestrutura de verdade (PostgreSQL e Kafka via Testcontainers, nunca H2 ou
um broker embarcado) validando cada etapa antes da próxima. Segundo, ele está
de fato publicado num ambiente acessível pela internet (Render + Aiven, ambos
tier gratuito), não apenas documentado como "rodável localmente" — o que
expôs, e forçou a resolver, problemas que só aparecem fora do laptop de quem
escreveu o código (limite de conexões de um Postgres compartilhado, o Kafka
exigindo autenticação, nomes de serviço colidindo entre contas diferentes).

## 2. Arquitetura

```
                         ┌──────────────────────┐
                         │      Navegador        │
                         └──────────┬────────────┘
                                    │ HTTPS
                                    ▼
                         ┌──────────────────────┐
                         │   frontend (React)    │
                         │   SPA estática, Nginx  │
                         └──────────┬────────────┘
                                    │ REST (4 origens diferentes,
                                    │ sem API gateway)
              ┌─────────────────────┼─────────────────────┬───────────────────┐
              ▼                     ▼                     ▼                   ▼
     ┌────────────────┐   ┌─────────────────┐   ┌───────────────────┐  ┌─────────────────────┐
     │  auth-service   │   │  orders-service  │   │ inventory-service │  │ notification-service │
     │  :8081          │   │  :8082           │   │  :8083             │  │  :8084                │
     │  JWT (RS256)    │◄──┤  cria pedidos    │──►│  catálogo, estoque │  │  histórico de avisos  │
     │  JWKS público   │   │                  │   │                    │  │                       │
     └───────┬─────────┘   └────────┬─────────┘   └─────────┬──────────┘  └───────────┬───────────┘
             │                      │                        │                         │
             │ REST (login da       │  Kafka: OrderCreated    │ Kafka: StockReserved /  │
             │ conta de serviço)    │  ────────────────────►  │ StockRejected           │
             │                      │  ◄────────────────────  │ ───────────────────────►│
             │                      │                                                    │
             ▼                      ▼                        ▼                           ▼
        ┌─────────┐           ┌──────────┐             ┌────────────┐              ┌──────────────┐
        │ auth db │           │orders db │             │inventory db│              │notification db│
        └─────────┘           └──────────┘             └────────────┘              └──────────────┘

     Kafka (broker único, 4 tópicos): orders.order-created, inventory.stock-reserved,
     inventory.stock-rejected, dead-letter-topic
```

### O que cada serviço faz

- **`auth-service`** — dono da identidade. Cadastra clientes (`CLIENTE`),
  autentica por e-mail/senha, emite tokens JWT assinados com a própria chave
  privada e publica a chave pública num endpoint JWKS
  (`/.well-known/jwks.json`) para que os outros três validem tokens sem nunca
  ter acesso à chave privada. Na subida, cria (se ainda não existirem) uma
  conta `ADMIN` e uma conta `SERVICE`, a partir de variáveis de ambiente.
- **`orders-service`** — dono do ciclo de vida do pedido. Recebe a criação,
  consulta o catálogo do `inventory-service` via REST para validar produtos e
  buscar o preço (nunca confia no preço que o cliente manda), grava o pedido
  como `PENDING`, e dispara a saga publicando `OrderCreated` no Kafka. Depois,
  consome as respostas (`StockReserved`/`StockRejected`) para decidir o
  desfecho.
- **`inventory-service`** — dono do catálogo e do estoque. Expõe o catálogo
  publicamente (leitura livre, escrita só para `ADMIN`), e reage a
  `OrderCreated` tentando reservar estoque de forma transacional e tudo-ou-nada
  (se faltar um item, nenhum é separado). Publica o resultado de volta no
  Kafka.
- **`notification-service`** — consumidor puro. Escuta os três tópicos de
  negócio (`OrderCreated`, `StockReserved`, `StockRejected`) num grupo de
  consumidores próprio e independente, e registra (simula o envio de) uma
  notificação por e-mail para cada desfecho. Não participa da saga: uma falha
  aqui nunca afeta o resultado do pedido.
- **`frontend`** — SPA em React que fala diretamente com os quatro serviços
  acima (não existe API gateway nem backend-for-frontend). Cobre a jornada do
  cliente (catálogo, carrinho, checkout, acompanhamento do pedido,
  notificações) e um painel administrativo simples (cadastro de produto,
  entrada de estoque).

### Síncrono vs. assíncrono

| Interação | Transporte | Por quê |
| --- | --- | --- |
| Front-end → qualquer serviço | REST (HTTP) | É a fronteira com o usuário; precisa de resposta imediata. |
| `orders-service` → `inventory-service` (consultar catálogo/preço) | REST síncrono (`RestClient`) | O pedido não pode ser criado sem saber se o produto existe e quanto custa — é uma dependência de leitura, não um evento de negócio. |
| `orders-service` → `inventory-service` (liberar reserva ao cancelar) | REST síncrono, com token de conta de serviço | Compensação pontual de uma saga já resolvida; não há motivo para passar pelo Kafka. |
| `orders-service` → `inventory-service` (pedido criado) | Kafka (`orders.order-created`) | O resultado da reserva não precisa (e não deve) bloquear a resposta HTTP da criação do pedido; a saga é assíncrona por natureza. |
| `inventory-service` → `orders-service` (resultado da reserva) | Kafka (`inventory.stock-reserved` / `inventory.stock-rejected`) | Mesma razão, na direção contrária. |
| `inventory-service`/`orders-service` → `notification-service` | Kafka (mesmos tópicos, grupo de consumidores separado) | Notificação é um efeito colateral do desfecho, não uma dependência dele. |

## 3. Decisões técnicas e o porquê de cada uma

### Maven multi-módulo com o módulo `contracts`

**Escolhido**: um único repositório Maven, um POM pai que só agrega módulos e
gerencia versões (`packaging: pom`, importando o BOM do Spring Boot em vez de
herdar `spring-boot-starter-parent`), mais um módulo `contracts` que contém
exclusivamente os *records* dos eventos Kafka (`OrderCreatedEvent`,
`StockReservedEvent`, `StockRejectedEvent`, os nomes dos tópicos).

**Alternativas**: (a) cada serviço em um repositório próprio, sem nada
compartilhado — cada um definiria sua própria cópia do contrato do evento; ou
(b) uma biblioteca de domínio compartilhada, com entidades e regras de
negócio comuns.

**Por quê**: a opção (a) duplica o contrato do evento em quatro lugares e
deixa a divergência (um campo renomeado num produtor, esquecido num
consumidor) só aparecer em produção. A opção (b) — indo longe demais na
direção oposta — acopla os serviços em tempo de execução: mudar uma entidade
de domínio de um serviço passaria a exigir recompilar os outros. O módulo
`contracts` fica no meio: o produtor e os consumidores de um evento
compartilham o mesmo tipo Java, então um campo que muda quebra a compilação
em vez de falhar silenciosamente em runtime — mas nada além do contrato do
evento é compartilhado; entidades JPA, DTOs de API e regras de negócio são
exclusivos de cada serviço.

### Java 17, não 21 nem 8

A recomendação inicial (minha, ao propor o plano) foi Java 21 — LTS mais
recente, com *virtual threads*, *pattern matching* para `switch` e
*sequenced collections* estabilizados. A decisão tomada, explicitamente,
foi Java 17. É uma escolha defensável por conta própria: 17 é o LTS mais
amplamente adotado em ambientes corporativos que já rodam Spring Boot 3.x
hoje, com o ecossistema de bibliotecas mais maduro nessa versão — para um
projeto deste porte, nenhum dos recursos exclusivos do 21 (virtual threads
em particular, que ajudariam sob alta concorrência de I/O) era necessário
para o que estava sendo demonstrado. Java 8 nunca foi uma opção real: Spring
Boot 3.x exige Java 17 como piso mínimo.

### Saga coreografada sem *outbox*

Uma **saga** é o padrão para manter consistência entre serviços que não
compartilham uma transação de banco: em vez de um `COMMIT` atômico
envolvendo `orders` e `inventory`, cada serviço faz sua própria mudança local
e publica um evento; o estado "final" emerge da sequência de eventos, não de
uma transação distribuída. Este projeto usa a variante **coreografada** (sem
orquestrador central) — mais simples de operar num sistema deste tamanho, ao
custo de a lógica do fluxo ficar espalhada entre os serviços em vez de
centralizada num único lugar.

O **padrão outbox** resolve um problema específico dessa arquitetura: como
publicar um evento no Kafka *na mesma transação* que grava o estado local no
banco, já que Kafka e PostgreSQL não compartilham uma transação distribuída?
A resposta do outbox é gravar o evento numa tabela de saída dentro da mesma
transação do banco, e um processo separado (um poller ou o Debezium via CDC)
lê essa tabela e publica no Kafka de forma garantida.

Este projeto **não implementa outbox** — foi uma escolha explícita de
escopo, não um esquecimento. O que existe em vez disso, em
`OrderEventPublisher` (orders-service):

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void publish(OrderCreatedDomainEvent domainEvent) {
    // ...publica no Kafka depois que a transação já deu commit
}
```

O evento só é publicado depois que a transação do pedido confirma o commit —
isso evita o problema mais óbvio (anunciar um pedido que acabou sofrendo
rollback). Mas fica uma janela real e documentada no próprio código: **se o
processo cair entre o commit e o envio ao Kafka**, o pedido fica gravado como
`PENDING` para sempre, sem que o evento saia. É o trade-off consciente de não
ter outbox — o comentário no código chama isso de "o próximo passo natural
caso o projeto evolua".

### `auth-service` dedicado emitindo JWT

**Escolhido**: um serviço de identidade separado, que é o único a ter a
chave privada RSA; os outros três só têm a chave pública, obtida
dinamicamente via JWKS.

**Alternativa**: o `orders-service` (ou qualquer outro) emitir os próprios
tokens, com um segredo compartilhado (HS256) entre todos.

**Por quê**: com HS256 e um segredo único, qualquer serviço capaz de
*validar* um token também seria capaz de *emitir* um — um serviço
comprometido poderia forjar um token de administrador. Separar emissão
(assinatura com chave privada, só no `auth-service`) de validação
(verificação com chave pública, em todos) elimina essa superfície. A
identidade também precisa existir num lugar único e neutro: colocá-la dentro
do `orders-service` misturaria a responsabilidade de "quem é você" com "o
que você pediu".

### Outras decisões relevantes

| Decisão | Alternativa considerada | Por quê venceu |
| --- | --- | --- |
| Kafka em modo KRaft (sem Zookeeper) | Zookeeper + Kafka | O Zookeeper foi removido no Kafka 4.0; usá-lo num projeto novo seria adotar algo já obsoleto. |
| `RestClient` (Spring Framework 6) para chamadas síncronas | OpenFeign | O Spring Cloud OpenFeign está em modo manutenção; `RestClient` é nativo do framework, sem dependência extra. |
| Bloqueio pessimista (`SELECT ... FOR UPDATE`) na reserva de estoque, com ordem determinística por id de produto | Bloqueio otimista (`@Version`) | Sob concorrência real (dois pedidos disputando o mesmo produto), o otimista falharia e exigiria retry explícito; o pessimista serializa o acesso na hora. A ordem determinística evita deadlock entre duas reservas que disputam os mesmos produtos em ordens diferentes — provado por um teste de concorrência real (`StockReservationConcurrencyTest`), não só por inspeção. |
| Dead-letter topic com retry limitado (3 tentativas, 1s de intervalo) | Deixar o Kafka reentregar indefinidamente | Uma mensagem que sempre falha (bug de deserialização, por exemplo) travaria a partição inteira e impediria o avanço de todos os pedidos seguintes; o DLT a tira do caminho. |
| Idempotência por `processed_events` (tabela com o `eventId` como chave primária) | Confiar que o Kafka nunca reentrega | A entrega do Kafka é *at-least-once* por definição; sem essa tabela, uma reentrega confirmaria/rejeitaria o mesmo pedido duas vezes. A marca é gravada na mesma transação da mudança de estado — ou as duas valem, ou nenhuma. |
| Testcontainers desde o primeiro teste, nunca H2 | H2 em memória para os testes | H2 esconde divergências reais de dialeto SQL, tipos e comportamento das migrations Flyway em relação ao PostgreSQL que roda em produção. |
| Papéis (`roles` claim) em vez de escopos OAuth2 (`scope`) | Usar o suporte padrão do Spring Security para `scope`/`scp` | O modelo do projeto é RBAC simples (papel do usuário), não OAuth2 de terceiros; exigiu um `JwtRoleConverter` próprio em cada serviço, já que o conversor padrão do Spring só entende `scope`. |
| Conta de serviço (`SERVICE`) para a compensação da saga | Propagar o token do cliente até o Kafka e usá-lo na compensação | O evento do Kafka não carrega usuário autenticado, e mesmo que carregasse, quem pede a liberação da reserva é o *serviço*, não a pessoa — atribuir a ação ao cliente seria semanticamente errado. |
| CORS liberado por variável de ambiente (`CORS_ALLOWED_ORIGINS`), em vez de um API gateway | Um gateway único na frente dos quatro serviços | Manter o escopo do projeto no que ele se propõe a demonstrar; um gateway seria uma peça de infraestrutura a mais sem ensinar nada de novo sobre o domínio. O custo real: o front-end precisa saber a URL dos quatro serviços. |
| `DB_POOL_MAX_SIZE` configurável, usado para reduzir o pool do Hikari a 2 conexões por serviço no deploy real | Manter o padrão do Hikari (10 por serviço) | Descoberto na prática, não antecipado: o Postgres gratuito da Aiven tem teto de 20 conexões e nenhum pooling (PgBouncer) por cima; 4 serviços × 10 conexões default estourava o limite e derrubava o deploy. |
| Consolidar os *dead-letter topics* num único `dead-letter-topic` | Um `.DLT` por tópico de origem (padrão do Spring Kafka) | Também descoberto na prática: o padrão geraria 6 tópicos (3 de negócio + 3 DLT), e o tier gratuito de Kafka da Aiven permite no máximo 5. |

## 4. Regras de negócio implementadas

Esta seção descreve o que o código realmente faz, verificado lendo os
serviços de domínio (`OrderService`, `Order`, `StockReservationService`,
`AuthService`) — não o que foi pedido originalmente.

### Papéis e permissões

| Recurso | Anônimo | `CLIENTE` | `ADMIN` | `SERVICE` |
| --- | --- | --- | --- | --- |
| Ler catálogo (`GET /products`) | Permitido | Permitido | Permitido | Permitido |
| Alterar catálogo / estoque | 401 | 403 | Permitido | 403 |
| Criar pedido | 401 | Permitido | 403 | 403 |
| Ver um pedido específico | 401 | Só o próprio | Todos | 403 |
| Listar pedidos | 401 | Só os próprios (filtro imposto, não aceito da requisição) | Todos, com filtro por cliente opcional | 403 |
| Cancelar pedido | 401 | Só o próprio, e só se `PENDING` | Não é dono da ação (não compra em nome de ninguém) | — |
| Reservas de estoque (endpoint interno) | 401 | 403 | Permitido | Permitido |
| `/actuator/prometheus`, `/actuator/metrics/**` | 401 | 403 | Permitido | Permitido |

- `CLIENTE` nasce assim no cadastro público; não há forma de um cliente se
  autopromover a `ADMIN` pela API. Contas `ADMIN` e `SERVICE` só existem via
  *bootstrap* na subida do `auth-service`, a partir de variáveis de ambiente.
- Buscar um pedido de outro cliente devolve **404, não 403** — confirmar que
  o pedido existe já seria informação demais para quem não é dono dele.
- Login (e-mail inexistente, senha errada, conta desativada) devolve sempre a
  **mesma mensagem de erro** — diferenciar os casos permitiria descobrir
  quais e-mails têm conta cadastrada.
- Senha de cadastro: mínimo de 12 caracteres (sem exigência de símbolos —
  comprimento mínimo protege mais que regras decoradas em senhas curtas).
  Hash com BCrypt, custo 12.

### Criação e ciclo de vida do pedido

- Um pedido nasce sempre `PENDING`. Não é possível criá-lo em outro estado.
- **O preço nunca vem do cliente**: o `orders-service` busca o preço atual no
  catálogo do `inventory-service` no momento da criação, numa única chamada
  em lote (evita N+1 por item).
- Produto inexistente **ou inativo** são tratados exatamente da mesma forma
  (pedido rejeitado com 422) — distinguir os dois casos revelaria ao cliente
  o que existe no catálogo sem estar à venda.
- Um mesmo produto duplicado no corpo da requisição é rejeitado (400) — o
  cliente deve mandar a quantidade correta numa única linha, não duas linhas
  do mesmo item.
- Um pedido precisa de pelo menos um item; a entidade `Order` recusa se
  construir sem itens (`IllegalArgumentException` na fábrica estática).
- **Transições de status válidas**: `PENDING → CONFIRMED`, `PENDING →
  REJECTED`, `PENDING → CANCELLED`. Qualquer outra transição lança
  `IllegalStateException`, traduzida pelo `GlobalExceptionHandler` em 409.
  Confirmar ou rejeitar um pedido que já está nesse mesmo estado é aceito
  silenciosamente (idempotência do lado da saga, não um erro).
- **Cancelamento** só é possível enquanto o pedido está `PENDING`. Existe uma
  condição de corrida tratada explicitamente: se o cliente cancela exatamente
  no meio da janela em que a reserva de estoque está em andamento, e a
  confirmação chega *depois* do cancelamento, o `orders-service` não reverte
  o cancelamento — ele libera a reserva que acabou de ser feita, via chamada
  REST de compensação ao `inventory-service` (autenticada com a conta de
  serviço).

### Reserva de estoque

- A reserva é **tudo ou nada**: se faltar estoque para qualquer item do
  pedido, nenhum item é reservado (a transação inteira é descartada antes de
  qualquer escrita).
- Reservas são travadas nas linhas de estoque em **ordem determinística**
  (por id do produto, ordenado), especificamente para que dois pedidos com os
  mesmos produtos, disputando as mesmas linhas em ordens diferentes, não
  caiam em deadlock.
- A reserva é **idempotente por `orderId`**: se o mesmo evento `OrderCreated`
  chegar duas vezes (a entrega do Kafka é *at-least-once*), a segunda
  chamada apenas devolve a reserva que já existe, sem separar estoque de
  novo.
- Confirmação e liberação de reserva também são idempotentes: se a reserva já
  foi resolvida (confirmada ou liberada), uma nova chamada devolve o estado
  atual sem tentar resolver de novo.

### Notificações

- Puramente reativas: nascem do consumo de eventos, nunca de uma chamada
  direta à API (não existe endpoint para "criar" uma notificação).
- Um cliente só enxerga as próprias notificações; o filtro por `customerId`
  é imposto da mesma forma que em pedidos (ignorado da requisição para quem
  não é `ADMIN`).
- Uma falha no envio (simulado) não derruba o processamento do evento: a
  notificação é registrada como `FAILED` e o evento é marcado como
  processado do mesmo jeito — deixar a exceção subir faria o Kafka reentregar
  o evento inteiro, reenviando a notificação para quem já a recebeu com
  sucesso.

## 5. Fluxo de dados de ponta a ponta

Narrativa de um pedido que **dá certo** (estoque suficiente):

1. **Cliente clica em "Finalizar pedido"** no front-end. O navegador faz
   `POST /api/v1/orders` no `orders-service`, com o token JWT do cliente e a
   lista de `{productId, quantity}` — sem preço, sem nome de produto.
2. **`orders-service` recebe a requisição.** Dentro de uma transação:
   rejeita produtos duplicados, chama `GET /api/v1/products/by-ids` no
   `inventory-service` (REST síncrono) para validar que todos os produtos
   existem, estão ativos, e pegar o preço atual. Monta o pedido como
   `PENDING`, grava no banco `orders`, e devolve `201 Created` ao cliente
   imediatamente — o cliente já vê o pedido, com status `PENDING`.
3. **Só depois que a transação der commit** (`@TransactionalEventListener`
   com `AFTER_COMMIT`), o `orders-service` publica `OrderCreated` no tópico
   `orders.order-created`, com a chave de partição sendo o `orderId` (mantém
   a ordem dos eventos de um mesmo pedido) e o id de correlação da
   requisição HTTP original anexado como header Kafka.
4. **`inventory-service` consome `OrderCreated`.** Verifica se já processou
   esse `orderId` antes (idempotência); se não, trava as linhas de estoque
   dos produtos pedidos em ordem determinística (`SELECT ... FOR UPDATE`),
   confere disponibilidade de cada item e, se houver estoque para todos,
   grava as reservas e publica `StockReserved` no tópico
   `inventory.stock-reserved`.
5. **Ao mesmo tempo, `notification-service` também consumiu `OrderCreated`**
   (grupo de consumidores independente) e já registrou/"enviou" a primeira
   notificação — "pedido recebido" — sem esperar o resultado da reserva.
6. **`orders-service` consome `StockReserved`.** Verifica idempotência pelo
   `eventId`, confirma que o pedido ainda está `PENDING` (não foi cancelado
   nesse meio-tempo), e muda o status para `CONFIRMED`.
7. **`notification-service` consome `StockReserved`** e registra/"envia" a
   segunda notificação — "pedido confirmado".
8. **O front-end**, que estava consultando `GET /api/v1/orders/{id}`
   periodicamente enquanto o pedido estava `PENDING`, vê o status virar
   `CONFIRMED` e para de consultar.

Se o estoque **não** for suficiente, o passo 4 publica `StockRejectedEvent`
(com a lista de faltas por produto) em vez de `StockReserved`; o
`orders-service` marca o pedido como `REJECTED` com o motivo, e o
`notification-service` envia "pedido rejeitado" explicando o que faltou —
sem que nenhuma linha de estoque tenha sido alterada (a rejeição acontece
antes de qualquer escrita).

## 6. Perguntas prováveis de entrevista + respostas

**1. Por que uma saga coreografada e não uma orquestrada (com um serviço
central comandando o fluxo)?**
Para este tamanho de sistema (dois passos: reservar estoque, confirmar
pedido), um orquestrador central seria uma peça de infraestrutura extra sem
ganho real. A coreografia mantém cada serviço dono da sua própria decisão —
o `inventory-service` decide se reserva, o `orders-service` decide o que
fazer com o resultado — ao custo de a lógica do fluxo completo não estar
visível num único lugar; para entender a saga inteira, é preciso ler os dois
serviços.

**2. O que acontece se o `orders-service` cair exatamente entre o commit da
transação e o envio do evento ao Kafka?**
O pedido fica gravado como `PENDING` para sempre — o evento nunca sai, então
o `inventory-service` nunca fica sabendo que o pedido existe. É a janela
conhecida e documentada da não implementação do outbox pattern. A correção
correta seria gravar o evento na mesma transação (numa tabela de saída) e um
processo separado publicá-lo de forma garantida a partir dali.

**3. Como o sistema lida com mensagens duplicadas do Kafka?**
Toda entrega Kafka aqui é tratada como *at-least-once*: cada consumidor
verifica antes de processar se já viu aquele `eventId`, usando uma tabela
`processed_events` com o `eventId` como chave primária, gravada na mesma
transação da mudança de estado. Se a transação falhar, a marca de "já
processado" volta atrás junto, então o reprocessamento continua permitido.

**4. Por que bloqueio pessimista na reserva de estoque, e não otimista?**
Bloqueio otimista (campo de versão) detectaria o conflito só na hora do
commit, exigindo lógica de retry explícita quando dois pedidos disputam o
mesmo produto. O pessimista (`SELECT ... FOR UPDATE`) serializa o acesso na
hora certa. O risco do pessimista é deadlock quando duas transações travam
as mesmas linhas em ordens diferentes — resolvido travando sempre na mesma
ordem determinística (por id do produto).

**5. Por que RS256 (assimétrico) e não HS256 (segredo compartilhado) para o
JWT?**
Com HS256, qualquer serviço capaz de *validar* um token teria o mesmo
segredo usado para *assiná-lo* — um serviço comprometido poderia forjar
tokens de administrador. Com RS256, só o `auth-service` tem a chave privada;
os outros buscam a chave pública dinamicamente via JWKS e só conseguem
validar, nunca emitir.

**6. Por que um `auth-service` separado em vez de o `orders-service` cuidar
de login?**
Separação de responsabilidade: "quem é você" é uma pergunta independente de
"o que você quer fazer". Colocar emissão de identidade dentro de um serviço
de domínio (pedidos) misturaria as duas coisas e criaria uma dependência
estranha dos outros serviços em relação ao `orders-service` só para validar
token.

**7. Como o `orders-service` chama o `inventory-service` na compensação
(liberar reserva ao cancelar) sem ter um usuário logado no contexto?**
Existe uma conta de serviço (papel `SERVICE`), com credenciais próprias.
`ServiceTokenProvider` faz login nela, guarda o token em memória e renova
pouco antes de expirar — evita autenticar a cada chamada. Propagar o token
do cliente seria semanticamente errado: quem está pedindo a liberação é o
serviço, reagindo a um evento, não a pessoa.

**8. O sistema teria como escalar horizontalmente?**
Os serviços em si são stateless (nenhum estado de sessão em memória — a
autenticação é o token em cada requisição) e escalariam horizontalmente sem
mudança de código. O ponto real de atenção seria o banco: hoje cada serviço
tem uma única instância PostgreSQL sem réplicas, e o bloqueio pessimista na
reserva de estoque serializa escritas concorrentes no mesmo produto — sob
carga alta e muita concorrência no mesmo produto, esse serializar vira
gargalo. Resolver isso é uma questão de infraestrutura de banco (réplicas,
particionamento), não de reescrever a lógica de negócio.

**9. Por que Testcontainers em vez de H2 para os testes?**
H2 não fala o mesmo dialeto SQL do PostgreSQL, tem tipos diferentes, e as
migrations Flyway rodam contra ele de um jeito que pode divergir do banco
real. Testcontainers sobe um PostgreSQL (e, onde precisa, um Kafka) reais
via Docker para os testes, ao custo de testes mais lentos — a troca vale a
pena porque elimina uma classe inteira de "passou no teste, quebrou em
produção".

**10. O front-end guarda o token JWT em `localStorage` — isso não é um
risco de segurança?**
É, e é um trade-off consciente, documentado no próprio README: em caso de
XSS bem-sucedido, o token fica exposto. A alternativa mais segura seria um
cookie `httpOnly` emitido por um backend-for-frontend — mas isso exigiria
uma peça de infraestrutura que este projeto deliberadamente não tem (não há
API gateway nem BFF; o front-end fala direto com os quatro serviços). Para
uma SPA sem esse componente, `localStorage` é a opção mais simples
disponível.

**11. O que o teste de concorrência (`StockReservationConcurrencyTest`)
realmente prova?**
Ele dispara duas reservas concorrentes para o mesmo produto com estoque
insuficiente para as duas, e prova que só uma consegue — sem o `SELECT ...
FOR UPDATE`, as duas transações leriam a mesma disponibilidade antes de
qualquer uma escrever, e ambas reservariam, deixando o estoque negativo. É
uma prova por execução real contra um Postgres real (via Testcontainers), não
uma inspeção do código.

**12. Por que consolidar os *dead-letter topics* num só, em vez de manter o
padrão do Spring Kafka (um `.DLT` por tópico)?**
Foi uma mudança feita durante o deploy real, não antecipada: o padrão do
Spring geraria 6 tópicos (3 de negócio + 3 `.DLT`), e o tier gratuito de
Kafka usado no deploy (Aiven) limita a 5 tópicos no total. Consolidar em um
`dead-letter-topic` único, com um resolvedor de destino customizado no
`DeadLetterPublishingRecoverer`, resolveu sem abrir mão do mecanismo.

**13. Como a observabilidade funciona sem um sistema de tracing distribuído
(Zipkin, Tempo)?**
Por um id de correlação manual: um filtro HTTP gera ou repassa um
`X-Correlation-Id`, ele entra no MDC (aparece em todo log JSON daquela
thread), é copiado para chamadas REST de saída via
`ClientHttpRequestInterceptor`, e anexado como header em cada mensagem
Kafka — o consumidor lê o header de volta para o próprio MDC antes de
processar. O resultado: dado um id, um grep nos quatro serviços reconstrói a
jornada completa de um pedido. O custo de implementação é baixo; um sistema
de tracing de verdade seria o passo natural seguinte, não um requisito deste
porte de projeto.

**14. O que você mudaria numa versão de produção real deste sistema?**
Implementar o outbox pattern (fechar a janela do passo 3 do fluxo de
dados), colocar um sistema de tracing distribuído de verdade, adicionar rate
limiting e um API gateway na frente dos quatro serviços, cobrir o
`auth-service` com testes de verdade (hoje só tem um teste de contexto
subindo, nenhum teste de unidade ou integração real — ver seção 7), e trocar
o `localStorage` do front-end por um fluxo de cookie `httpOnly` via
backend-for-frontend.

**15. Por que o catálogo é público (qualquer um pode ler, sem login) mas
escrever nele exige `ADMIN`?**
Modela uma loja real: navegar por produtos e ver preços não deveria exigir
conta — isso reduz fricção para quem só está pesquisando. Alterar o que está
à venda, sim, é uma operação administrativa.

## 7. Limitações conhecidas e melhorias futuras

Simplificações deliberadas de escopo de portfólio:

- **Sem outbox pattern** — a janela entre commit e publicação do evento
  `OrderCreated` (seção 3) é o compromisso mais importante do projeto,
  documentado no próprio código-fonte.
- **Sem API gateway / backend-for-frontend** — o front-end fala direto com
  os quatro serviços, cada um com CORS liberado por variável de ambiente.
  Numa versão de produção, um gateway central resolveria roteamento,
  rate limiting e ocultaria a topologia interna do cliente.
- **Sem sistema de tracing distribuído** — a correlação de logs é manual
  (id propagado por header + MDC), não instrumentada por uma ferramenta como
  Zipkin ou Tempo.
- **`/actuator/prometheus` exposto, mas nenhum Prometheus real coletando** —
  o endpoint existe e está protegido corretamente; não foi empacotado um
  servidor Prometheus no `docker-compose.yml`, para não embutir uma stack de
  observabilidade inteira num projeto deste porte.
- **JWT em `localStorage` no front-end**, não em cookie `httpOnly` — troca
  consciente de simplicidade por uma superfície de risco a mais em caso de
  XSS (ver pergunta 10).
- **Infraestrutura de deploy no tier gratuito** — Render (compute) e Aiven
  (Postgres/Kafka), ambos com limitações reais que precisaram de ajuste de
  código para funcionar: pool de conexões do Hikari reduzido para 2 por
  serviço (o Postgres gratuito da Aiven tem teto de 20 conexões, sem
  pooling), tópicos Kafka consolidados para caber no limite de 5 do tier
  gratuito, e duas características operacionais que uma demonstração
  precisa levar em conta: os serviços do Render dormem após 15 minutos sem
  tráfego, e o Kafka da Aiven desliga sozinho após 24h sem atividade e
  precisa ser religado manualmente pelo console.

Uma lacuna real, não deliberada, encontrada ao revisar o código para este
documento:

- **O `auth-service` não tem testes de unidade ou integração** — só existe
  um `AuthApplicationTests` (o teste padrão de "o contexto Spring sobe sem
  erro"). Os outros três serviços têm suítes reais: `orders-service` e
  `inventory-service` têm testes de controller (`@WebMvcTest`), de serviço
  (Mockito), de repositório e de integração via Testcontainers, incluindo um
  teste de concorrência real; `notification-service` tem testes de serviço e
  de listener. `auth-service` ficou para trás nesse quesito — se fosse
  continuar o projeto, seria o primeiro débito técnico a pagar.

## 8. Roteiro de demonstração

Pressupõe que o deploy (Render + Aiven) já está no ar — ver `DEPLOY.md` para
subir do zero.

**Antes de começar** (fazer alguns minutos antes de mostrar para alguém):

1. Abra a URL do front-end uma vez, sozinho, para os serviços do Render
   acordarem — eles dormem após 15 minutos sem tráfego e levam de 30 a 60
   segundos para responder no primeiro acesso depois de dormir.
2. No console da Aiven, confira se o serviço **Kafka está "Running"** — ele
   desliga sozinho depois de 24h sem atividade e não acorda sozinho; sem
   isso, a saga nunca fecha e o pedido fica preso em "Pendente".

**Roteiro**:

1. Abra a URL do front-end. Mostre o catálogo público — sem estar logado, já
   dá para navegar pelos produtos.
2. Entre como administrador (`admin@ecommerce.local` + a senha configurada
   no deploy). Vá ao painel **Admin** e cadastre um produto novo, com
   estoque inicial pequeno (ex.: 5 unidades) — mostra que a escrita no
   catálogo exige essa conta.
3. Saia e abra uma aba anônima (ou saia da conta admin). Cadastre-se como
   cliente novo pela tela de cadastro.
4. No catálogo, adicione o produto recém-criado ao carrinho e finalize o
   pedido. O pedido aparece com status **Pendente**.
5. Aguarde alguns segundos na tela do pedido — o front-end consulta o status
   periodicamente. Ele deve virar **Confirmado** sozinho: esse é o momento
   de explicar que, por trás, o `orders-service` publicou um evento no
   Kafka, o `inventory-service` reservou o estoque de forma independente, e
   a confirmação voltou por outro evento — nenhuma chamada HTTP direta entre
   os dois fez isso acontecer.
6. Vá em **Notificações** e mostre as duas mensagens gerada pelo mesmo
   pedido ("pedido recebido" e "pedido confirmado") — evidência de que o
   `notification-service` consumiu os mesmos eventos, de forma
   independente da saga.
7. Opcional, para mostrar o caminho de rejeição: peça uma quantidade maior
   do que o estoque disponível num novo pedido, e mostre que ele vai para
   **Rejeitado** com o motivo explicado, e que uma notificação de rejeição
   também chega — sem que o estoque do produto tenha sido alterado.
