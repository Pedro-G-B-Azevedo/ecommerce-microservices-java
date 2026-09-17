# ecommerce-microservices-java

[![CI](https://github.com/Pedro-G-B-Azevedo/ecommerce-microservices-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Pedro-G-B-Azevedo/ecommerce-microservices-java/actions/workflows/ci.yml)

Sistema de e-commerce simplificado construído com arquitetura de microsserviços em
Java/Spring Boot, usando PostgreSQL (um banco por serviço), Kafka para comunicação
assíncrona e Docker para orquestração local.

Projeto pessoal de portfólio, desenvolvido em etapas incrementais.

## Arquitetura

```mermaid
flowchart LR
    client([Cliente / React])

    client --> auth[auth-service<br/>:8081]
    client --> orders[orders-service<br/>:8082]
    client --> inventory[inventory-service<br/>:8083]

    orders -- "REST: valida produto e preço" --> inventory

    orders -- OrderCreated --> kafka{{Kafka}}
    kafka -- OrderCreated --> inventory
    inventory -- "StockReserved / StockRejected" --> kafka
    kafka -- "StockReserved / StockRejected" --> orders
    kafka -- eventos --> notification[notification-service<br/>:8084]

    auth --- authdb[(auth)]
    orders --- ordersdb[(orders)]
    inventory --- inventorydb[(inventory)]
    notification --- notificationdb[(notification)]
```

O fluxo de criação de pedido é uma **saga coreografada**: o pedido nasce `PENDING`,
o `inventory-service` tenta reservar o estoque e publica o resultado, e o
`orders-service` consome esse retorno para concluir o pedido como `CONFIRMED` ou
`REJECTED`. Os consumidores são idempotentes (deduplicação por id do evento) e
falhas repetidas vão para um *dead-letter topic*.

## Stack

| Camada | Tecnologia |
| --- | --- |
| Linguagem | Java 17 |
| Framework | Spring Boot 3.5 (Web, Data JPA, Validation, Security, Actuator) |
| Persistência | PostgreSQL 16 + Hibernate, migrations com Flyway |
| Mensageria | Apache Kafka (modo KRaft, sem Zookeeper) |
| Comunicação síncrona | `RestClient` (Spring Framework 6) |
| Autenticação | Spring Security + JWT (jjwt), papéis `CLIENTE` e `ADMIN` |
| Documentação | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5, Mockito, Testcontainers, JaCoCo |
| Build | Maven multi-módulo |
| Infra local | Docker Compose |
| CI | GitHub Actions |

## Estrutura do repositório

```
.
├── pom.xml                  # POM pai: versões, plugins e módulos
├── contracts/               # Contratos dos eventos Kafka (compartilhado)
├── auth-service/            # Usuários, papéis e emissão de JWT
├── orders-service/          # Criação e consulta de pedidos
├── inventory-service/       # Estoque e reserva de itens
├── notification-service/    # Notificações a partir dos eventos
├── docker-compose.yml       # Infraestrutura local
└── .github/workflows/ci.yml # Pipeline de build e testes
```

Os serviços compartilham apenas o módulo `contracts`, que contém somente os
*records* dos eventos publicados no Kafka. Entidades JPA, regras de negócio e
DTOs de API **não** são compartilhados: cada serviço é dono do seu domínio e do
seu banco.

## Como executar

### Pré-requisitos

- JDK 17 ou superior
- Docker e Docker Compose
- Não é necessário instalar o Maven: use o wrapper (`./mvnw`)

### Subir tudo

```bash
cp .env.example .env     # ajuste usuários e senhas
docker compose up -d --build
```

Sobe os quatro serviços, seus bancos e o Kafka. As dependências são declaradas por
`healthcheck`, não por ordem de inicialização: o `orders-service` só começa depois
que o banco aceita conexões, o Kafka responde e o `auth-service` está de pé para
servir a chave pública.

Para subir apenas a infraestrutura e rodar os serviços pela IDE:

```bash
docker compose up -d auth-db orders-db inventory-db notification-db kafka
```

Cada serviço sobe um PostgreSQL próprio:

| Serviço | Banco | Porta do host |
| --- | --- | --- |
| auth-service | `auth` | 5433 |
| orders-service | `orders` | 5434 |
| inventory-service | `inventory` | 5435 |
| notification-service | `notification` | 5436 |

### Build e testes

```bash
./mvnw verify
```

> Os testes usam **Testcontainers** e sobem um PostgreSQL real, portanto o Docker
> precisa estar em execução. As migrations Flyway e o mapeamento Hibernate são
> validados contra o mesmo banco usado em produção — nada de H2.

### Rodar um serviço

```bash
./mvnw -pl orders-service spring-boot:run
```

| Serviço | Porta | Swagger UI |
| --- | --- | --- |
| auth-service | 8081 | http://localhost:8081/swagger-ui.html |
| orders-service | 8082 | http://localhost:8082/swagger-ui.html |
| inventory-service | 8083 | http://localhost:8083/swagger-ui.html |
| notification-service | 8084 | http://localhost:8084/swagger-ui.html |

Cada serviço expõe também `/actuator/health`, `/actuator/info` e `/actuator/metrics`.

As credenciais de banco são lidas de variáveis de ambiente
(`ORDERS_DB_URL`, `ORDERS_DB_USER`, `ORDERS_DB_PASSWORD`, e equivalentes para os
demais serviços), com valores padrão apontando para o `docker-compose.yml`.

## API do orders-service

| Método | Rota | Descrição |
| --- | --- | --- |
| `POST` | `/api/v1/orders` | Cria um pedido (`201` + `Location`). O total é calculado a partir dos itens. |
| `GET` | `/api/v1/orders/{id}` | Busca um pedido com seus itens. |
| `GET` | `/api/v1/orders` | Lista pedidos, com filtros opcionais `customerId` e `status` e paginação. |
| `POST` | `/api/v1/orders/{id}/cancel` | Cancela um pedido que ainda esteja pendente. |

Erros seguem RFC 7807:

```json
{
  "type": "https://api.ecommerce.com/problems/invalid-order-state",
  "title": "Transição de status inválida",
  "status": 409,
  "detail": "Apenas pedidos pendentes podem ser cancelados; status atual: CANCELLED",
  "instance": "/api/v1/orders/edad184c-47e6-4ceb-822f-b578c0910a3c/cancel",
  "timestamp": "2026-01-15T10:00:00Z"
}
```

O corpo da criação traz apenas `productId` e `quantity`. **O preço não faz parte
do contrato**: o orders-service consulta o catálogo do inventory-service, em uma
única chamada em lote, e grava o preço vindo de lá. Aceitá-lo do cliente
permitiria que ele definisse quanto paga.

A integração distingue dois tipos de falha:

| Situação | Status | Por quê |
| --- | --- | --- |
| Produto inexistente ou inativo | `422` | O corpo está bem formado, mas referencia algo que o catálogo não vende. A resposta lista todos os ids problemáticos. |
| inventory-service fora do ar ou lento | `503` | É falha de infraestrutura, não do pedido: repetir mais tarde pode dar certo. |

Os tempos-limite da chamada são curtos (2s para conectar, 3s para ler): a criação
de pedido é síncrona, e uma chamada pendurada seguraria a thread e o cliente junto.

O `customerId` não faz parte do corpo: é o `sub` do token. Aceitá-lo permitiria
criar pedidos em nome de outra pessoa.

## API do inventory-service

| Método | Rota | Descrição |
| --- | --- | --- |
| `POST` | `/api/v1/products` | Cadastra um produto e abre sua linha de estoque. |
| `GET` | `/api/v1/products/{id}` | Busca um produto. |
| `GET` | `/api/v1/products/by-ids?ids=` | Consulta em lote, usada pelo orders-service. |
| `GET` | `/api/v1/products` | Lista produtos, com busca textual e filtro por situação. |
| `GET` | `/api/v1/products/{id}/stock` | Consulta disponível e reservado. |
| `POST` | `/api/v1/products/{id}/stock/replenish` | Dá entrada de estoque. |
| `POST` | `/api/v1/reservations` | Reserva estoque para um pedido (tudo ou nada, idempotente). |
| `GET` | `/api/v1/reservations/{orderId}` | Consulta a reserva de um pedido. |
| `POST` | `/api/v1/reservations/{orderId}/confirm` | Confirma: as unidades saem do estoque. |
| `POST` | `/api/v1/reservations/{orderId}/release` | Libera: as unidades voltam para disponível. |

O estoque tem duas quantidades: `availableQuantity`, o que pode ser vendido agora,
e `reservedQuantity`, o que já foi separado para pedidos ainda não resolvidos.
Reservar move unidades de uma para a outra; confirmar tira as reservadas de vez;
liberar as devolve.

Reservar usa `SELECT ... FOR UPDATE` sobre a linha de estoque. Sem isso, duas
requisições simultâneas leriam a mesma disponibilidade e ambas reservariam,
vendendo estoque que não existe. As linhas são travadas sempre na mesma ordem
(por id do produto), o que elimina a chance de deadlock entre pedidos que
compartilham produtos.

## A saga de estoque

```
orders                          Kafka                        inventory
  │                               │                              │
  ├─ grava pedido (PENDING) ──┐   │                              │
  │                        commit │                              │
  ├─ publica OrderCreated ────────┤                              │
  │                               ├──── OrderCreated ───────────>│
  │                               │                              ├─ reserva estoque
  │                               │<─ StockReserved / Rejected ──┤
  │<──────────────────────────────┤                              │
  ├─ CONFIRMED ou REJECTED        │                              │
```

O que sustenta esse fluxo:

**A publicação só acontece depois do commit.** `OrderService` dispara um evento
interno do Spring dentro da transação, e `OrderEventPublisher` o envia ao Kafka em
`@TransactionalEventListener(AFTER_COMMIT)`. Publicar antes anunciaria um pedido que
ainda pode sofrer rollback, e o inventory separaria estoque para algo que nunca
existiu.

**Resta uma janela conhecida.** Se o processo cair entre o commit e o envio, o
pedido fica `PENDING` sem que o evento saia. É o custo consciente de não usar o
padrão *outbox*, que gravaria o evento na mesma transação e o publicaria a partir
da tabela. É o próximo passo natural caso o projeto evolua.

**Consumo idempotente nos dois lados.** A entrega do Kafka é *at-least-once*: o
mesmo evento chega mais de uma vez, por exemplo quando o consumidor cai depois de
processar e antes de confirmar o offset. No inventory, a unique key
`(order_id, product_id)` e a checagem por pedido impedem reservar duas vezes. No
orders, a tabela `processed_events` tem o `eventId` como chave primária, e o
registro é gravado na mesma transação da mudança de status — ou as duas coisas
valem, ou nenhuma.

**Falha de negócio não é erro.** Estoque insuficiente é um desfecho: vira
`StockRejected`, não exceção. Reprocessar não mudaria o resultado, então a mensagem
não vai para o dead-letter topic. Já uma falha técnica sobe, é repetida três vezes
com um segundo de intervalo e, se persistir, vai para o DLT — sem isso, uma
mensagem que sempre falha trava a partição e impede o avanço de todos os pedidos
seguintes.

**Cancelamento durante a reserva é compensado.** Se o cliente cancela enquanto o
inventory separa o estoque, o `StockReserved` chega para um pedido já cancelado. O
pedido não volta atrás: o que se desfaz é a reserva, com uma chamada de liberação
ao inventory-service.

**A chave da mensagem é o id do pedido**, o que mantém todos os eventos de um mesmo
pedido na mesma partição e, portanto, em ordem.

### Tópicos

| Tópico | Produtor | Consumidor |
| --- | --- | --- |
| `orders.order-created` | orders-service | inventory-service |
| `inventory.stock-reserved` | inventory-service | orders-service |
| `inventory.stock-rejected` | inventory-service | orders-service |

Os contratos vivem no módulo `contracts`, compartilhado pelos serviços. É a única
coisa que eles compartilham — e é justamente o que garante que produtor e consumidor
concordem sobre o formato.

## O notification-service

Consumidor puro: não participa da saga e não influencia o resultado do pedido.
Assina os três tópicos em um grupo próprio, então uma falha aqui não afeta o
`orders` nem o `inventory`.

| Evento | Notificação |
| --- | --- |
| `OrderCreated` | "Recebemos o seu pedido" |
| `StockReserved` | "Seu pedido foi confirmado" |
| `StockRejected` | "Não conseguimos atender o seu pedido", detalhando cada item em falta |

O envio é simulado: `LoggingNotificationSender` escreve a mensagem no log em vez de
entregá-la. É uma implementação de `NotificationSender`, de modo que trocar o log
por um provedor real não exige tocar no serviço nem nos listeners.

Duas decisões valem o comentário:

**O serviço monta seu próprio modelo de leitura.** Os eventos de estoque trazem
apenas o pedido — o inventory-service não conhece o cliente, e fazê-lo repassar esse
dado o obrigaria a carregar informação que não é dele. Em vez disso, a tabela
`order_contacts` é preenchida a partir de `OrderCreated` e consultada quando o
desfecho chega. Se o desfecho chegar antes (Kafka só garante ordem dentro de uma
partição, e são tópicos diferentes), a exceção sobe, o Kafka reentrega e a tentativa
seguinte encontra o cadastro.

**Falha de envio não derruba o consumo.** A notificação é gravada como `FAILED`,
com o motivo, e o evento segue processado. Deixar a exceção subir faria o Kafka
reentregar o evento e reenviar a mensagem para quem já a recebeu — pior do que
registrar a falha e seguir.

A API é somente leitura: `GET /api/v1/notifications`, com filtros por pedido,
cliente e situação. Notificações nascem do consumo de eventos, nunca de uma chamada.

## Autenticação e autorização

O `auth-service` emite tokens JWT; os outros três os validam como *resource servers*.

**Assinatura assimétrica (RS256), não segredo compartilhado.** Com HS256 e um
segredo único, todo serviço capaz de validar um token também seria capaz de emitir
um — um serviço comprometido poderia forjar credenciais de administrador. Aqui só o
`auth-service` tem a chave privada; os demais buscam a pública em
`/.well-known/jwks.json` e nunca conseguem assinar nada.

O `sub` do token é o id do usuário, não o e-mail: é o que os outros serviços usam
como `customerId`, e não muda se o e-mail mudar.

### Quem pode o quê

| Recurso | Anônimo | `CLIENTE` | `ADMIN` | `SERVICE` |
| --- | --- | --- | --- | --- |
| `GET /products/**` | ✅ | ✅ | ✅ | ✅ |
| `POST/PUT /products/**` | 401 | 403 | ✅ | 403 |
| `/reservations/**` | 401 | 403 | ✅ | ✅ |
| `POST /orders` | 401 | ✅ | 403 | 403 |
| `GET /orders/{id}` | 401 | só os próprios | todos | 403 |
| `GET /orders` | 401 | só os próprios | todos | 403 |
| `GET /notifications` | 401 | só as próprias | todas | 403 |

O catálogo é público de propósito: numa loja, navegar por produtos e preços não
exige conta. Criar pedido é exclusividade de `CLIENTE` — um administrador não compra
em nome de ninguém.

**O filtro por cliente é imposto, não aceito.** Nas listagens, o parâmetro
`customerId` só tem efeito para `ADMIN`; para um cliente, ele é substituído pelo
`sub` do token. Sem isso, bastaria informar outro id para ler os pedidos alheios.

**Pedido de outra pessoa devolve 404, não 403.** Confirmar que o pedido existe, mas
pertence a outro cliente, já é informação que o solicitante não deveria obter.

**O login não distingue os motivos da falha.** E-mail inexistente, senha errada e
conta desativada devolvem a mesma mensagem; diferenciá-las permitiria descobrir
quais e-mails têm conta. O cadastro com e-mail já usado segue a mesma regra.

### Identidade de serviço

A compensação da saga — liberar uma reserva quando o pedido foi cancelado — nasce de
um evento do Kafka, onde não há usuário autenticado para repassar. E propagar o token
do cliente seria errado: quem pede a liberação é o serviço, não a pessoa. Por isso o
`orders-service` tem uma conta própria, com o papel `SERVICE`, e obtém um token no
`auth-service` que mantém em memória até pouco antes de expirar.

### Chaves

As chaves RSA vêm em PEM por `JWT_PRIVATE_KEY` e `JWT_PUBLIC_KEY`. Quando não vêm, o
`auth-service` gera um par efêmero ao subir e registra um aviso: conveniente em
desenvolvimento, inviável em produção, onde reiniciar invalidaria todos os tokens em
circulação.

As contas de administrador e de serviço são criadas na primeira subida a partir de
configuração (`auth.bootstrap`), e não semeadas numa migration com hash de senha
fixo no repositório. **As senhas padrão do `.env.example` servem só para o
docker-compose local.**

## Documentação das APIs

Cada serviço publica sua própria especificação OpenAPI 3.1 e uma interface Swagger:

| Serviço | Swagger UI | Especificação |
| --- | --- | --- |
| auth-service | http://localhost:8081/swagger-ui.html | `/v3/api-docs` |
| orders-service | http://localhost:8082/swagger-ui.html | `/v3/api-docs` |
| inventory-service | http://localhost:8083/swagger-ui.html | `/v3/api-docs` |
| notification-service | http://localhost:8084/swagger-ui.html | `/v3/api-docs` |

Para experimentar as rotas protegidas: obtenha um token em
`POST /api/v1/auth/login` no auth-service, clique em **Authorize** no Swagger do
serviço desejado e cole apenas o token — o prefixo `Bearer` é acrescentado pela
interface.

O esquema de segurança está declarado por operação, e não no serviço inteiro: no
inventory-service, por exemplo, as quatro leituras de catálogo aparecem abertas e as
seis operações restantes aparecem com cadeado. A documentação reflete as regras que
o `SecurityFilterChain` realmente aplica.

## As imagens

Cada serviço tem um `Dockerfile` multi-estágio na sua pasta. O build é feito a
partir da **raiz do repositório**, porque é um projeto Maven multi-módulo e cada
serviço depende do POM pai e do módulo `contracts`:

```bash
docker build -f orders-service/Dockerfile -t ecommerce/orders-service .
```

Três decisões:

**Os POMs são copiados antes do código.** Enquanto as dependências não mudarem, o
Docker reaproveita a camada de download mesmo quando o código muda — o que separa um
rebuild de segundos de um de minutos.

**O jar é desmontado em camadas** (`jarmode=tools ... extract --layers`). Dependências,
loader e aplicação viram camadas distintas da imagem, então um deploy que só mudou
código reenvia poucos megabytes em vez do jar inteiro.

**O processo não roda como root.** A imagem final é um JRE Alpine com um usuário
`spring` sem privilégios, e a memória é limitada por `MaxRAMPercentage` em vez de um
`-Xmx` fixo, para que a JVM respeite o limite do container qualquer que seja ele.

O CI constrói as quatro imagens a cada push e falha se alguma delas acabar rodando
como root.

## O pipeline de CI/CD

Um único workflow (`.github/workflows/ci.yml`), cinco jobs, cada um dependendo do
anterior ter passado:

```
build ──> images ──> security-scan ──> e2e ──> publish
          (build)     (Trivy)         (compose)  (só no main)
```

| Job | O que faz | Quando roda |
| --- | --- | --- |
| `build` | Testes unitários, web, persistência, mensageria e concorrência | Todo push e PR |
| `images` | Builda as quatro imagens; falha se alguma rodar como root | Todo push e PR |
| `security-scan` | Escaneia as imagens com Trivy | Todo push e PR |
| `e2e` | Sobe o `docker compose` completo e roda a jornada ponta a ponta | Todo push e PR |
| `publish` | Publica as imagens no GHCR | Só push em `main` |

**O scan de vulnerabilidades é um gate real, não decorativo.** Ele falha o job
para CVE **crítico e corrigido rio acima** (`severity: CRITICAL`,
`ignore-unfixed: true`) — travar o pipeline por uma vulnerabilidade que a própria
distro de base ainda não corrigiu não protegeria ninguém, só impediria qualquer
deploy indefinidamente. Vulnerabilidades `HIGH` são reportadas no log sem travar
o build, como informação para quem for revisar.

**A publicação só acontece depois dos dois gates de qualidade**, e só a partir do
`main` — o `publish` depende de `security-scan` e de `e2e`, e um push numa branch
de feature (como as `claude/**` deste projeto) deixa o job como *skipped*, não
como falha. Cada imagem é publicada com duas tags:

```
ghcr.io/<owner>/<repo>/<serviço>:sha-<commit curto>
ghcr.io/<owner>/<repo>/<serviço>:latest
```

A tag pelo SHA torna todo deploy rastreável até o commit exato que o gerou; a
`latest` é o que aponta para o estado mais recente do `main`. Os três jobs de
build, scan e publish compartilham o cache do Buildx por serviço
(`cache-from`/`cache-to: type=gha`), então a imagem publicada é a mesma que
passou pelo scan — sem rebuild às cegas entre um job e outro.

## Os testes

| Camada | O que cobre | Como roda |
| --- | --- | --- |
| Unitários | Regras de negócio, com Mockito | `./mvnw test` |
| Web | Contratos HTTP e regras de autorização, com MockMvc | `./mvnw test` |
| Persistência | Mapeamento e constraints, contra PostgreSQL real | Testcontainers |
| Mensageria | A saga dos dois lados, contra Kafka real | Testcontainers |
| Concorrência | Duas reservas simultâneas não vendem o mesmo estoque | Testcontainers |
| Ponta a ponta | A jornada pelos quatro serviços | `docker compose` |

Os cinco primeiros rodam em `./mvnw verify`, no CI a cada push. Nada de H2 nem de
broker embutido: testar contra um banco diferente do de produção esconde
divergências de dialeto, tipo e migration.

### O teste ponta a ponta

É o único que exercita os quatro serviços juntos — autenticação, catálogo, pedido,
saga pelo Kafka e notificação. Os demais cobrem cada serviço isoladamente e não
pegariam uma divergência de contrato entre dois deles.

```bash
docker compose up -d --build
./mvnw -pl e2e-tests verify -Dskip.e2e=false
```

Fica desligado por padrão, porque exige a pilha no ar — `./mvnw verify` precisa
funcionar em qualquer máquina. No CI, um job dedicado sobe o compose e o executa.

O módulo `e2e-tests` **não depende de Spring nem de nenhuma classe dos serviços**:
fala HTTP puro, como qualquer cliente externo. Se um contrato mudar, o teste quebra
— que é exatamente o que se espera dele.

## Convenções

- **Idioma**: identificadores, nomes de classe e mensagens de commit em inglês;
  comentários e documentação em português.
- **Commits**: [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `chore:`, `test:`, `docs:`, `refactor:`).
- **Pacotes por camada**: `controller`, `service`, `repository`, `entity`, `dto`,
  `exception`, `config`.
- **DTOs sempre**: entidades JPA nunca são expostas nas APIs.
- **Erros**: tratamento centralizado via `@RestControllerAdvice`, com respostas no
  formato [RFC 7807 Problem Details](https://www.rfc-editor.org/rfc/rfc7807).
- **Schema**: o Flyway é o dono do schema; o Hibernate roda com `ddl-auto: validate`.

## Decisões de arquitetura

| Decisão | Motivo |
| --- | --- |
| Maven multi-módulo | Build e pipeline únicos, versões de dependências alinhadas em um só lugar, sem acoplar os serviços em tempo de execução. |
| Módulo `contracts` | Evita duplicar o contrato dos eventos entre produtor e consumidores, sem virar uma biblioteca de domínio compartilhado. |
| Importar o BOM em vez de herdar `spring-boot-starter-parent` | O POM raiz não é um projeto Spring Boot; ele apenas agrega módulos e gerencia versões. |
| Kafka em modo KRaft | O Zookeeper foi removido no Kafka 4.0; usá-lo em um projeto novo seria legado. |
| Saga coreografada | Um pedido só é confirmado depois que o estoque responde — modela de verdade a consistência eventual entre serviços. |
| Publicação após o commit, sem outbox | Evita anunciar pedido que pode sofrer rollback. A janela entre commit e publicação fica documentada como limitação conhecida, em vez de escondida. |
| Dead-letter topic com retry limitado | Uma mensagem que sempre falha travaria a partição para sempre; o DLT a tira do caminho e a preserva para análise. |
| Testcontainers desde o início | Testar com H2 e implantar em PostgreSQL esconde divergências de dialeto, tipos e migrations. |
| `RestClient` em vez de OpenFeign | O Spring Cloud OpenFeign está em modo manutenção; o `RestClient` é nativo do Spring Framework. |
| JWT assinado com RS256 e distribuído por JWKS | Com segredo compartilhado, qualquer serviço que valida um token também poderia emitir um. |
| Conta de serviço para a compensação | O evento do Kafka não tem usuário autenticado, e propagar o token do cliente atribuiria a ele uma ação que é do serviço. |
| Scan de vulnerabilidades ignorando CVE sem correção | Travar o pipeline por uma falha que a própria distro de base ainda não corrigiu não protege ninguém, só impede todo deploy. |
| Publicação de imagem só no `main`, após os gates | Uma branch de feature não deve poder publicar; o *deploy* nasce da mesma verificação que valida o código. |
| Tomcat embarcado pinado acima do gerenciado pelo Boot 3.5.16 | O BOM traz `tomcat-embed-core 10.1.55`, com três CVEs críticos já corrigidos rio acima; sobrescrever a versão de um artefato de um BOM `import` exige uma entrada explícita de `dependencyManagement` antes do import, porque a property `${tomcat.version}` já veio resolvida do POM publicado. |

## Roadmap

- [x] **1.** Setup do projeto: estrutura multi-módulo, POMs, Docker Compose com os bancos, CI
- [x] **2.** `orders-service`: entidades, CRUD, DTOs, tratamento de erros e testes
- [x] **3.** `inventory-service`: produtos, estoque e reserva
- [x] **4.** Integração via Kafka entre `orders` e `inventory` (saga, idempotência, DLT)
- [x] **5.** `notification-service` consumindo os eventos
- [x] **6.** `auth-service`: Spring Security + JWT, papéis `CLIENTE`/`ADMIN`
- [x] **7.** Documentação OpenAPI completa
- [x] **8.** Testes de integração ponta a ponta
- [x] **9.** Dockerfiles e Docker Compose completo
- [x] **10.** Pipeline de CI/CD com build de imagens
- [ ] **11.** Observabilidade: correlation id, métricas e logs estruturados *(opcional)*
- [ ] **12.** Front-end em React *(opcional)*

## Licença

[MIT](LICENSE)
