-- Notificações enviadas. O histórico fica gravado para que seja possível auditar
-- o que foi comunicado ao cliente e quando.
CREATE TABLE notifications (
    id          UUID         PRIMARY KEY,
    order_id    UUID         NOT NULL,
    customer_id UUID         NOT NULL,
    type        VARCHAR(30)  NOT NULL,
    channel     VARCHAR(20)  NOT NULL,
    recipient   VARCHAR(320) NOT NULL,
    subject     VARCHAR(200) NOT NULL,
    body        TEXT         NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    failure_reason VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL,
    sent_at     TIMESTAMPTZ
);

CREATE INDEX ix_notifications_order_id ON notifications (order_id);
CREATE INDEX ix_notifications_customer_id ON notifications (customer_id);
CREATE INDEX ix_notifications_created_at ON notifications (created_at DESC);

-- Modelo de leitura próprio do serviço.
--
-- Os eventos de estoque trazem apenas o pedido: o inventory-service não conhece o
-- cliente, e fazê-lo repassar esse dado o obrigaria a carregar informação que não
-- é dele. Em vez disso, o notification-service monta seu próprio cadastro a partir
-- de OrderCreated e o consulta quando o desfecho chega.
CREATE TABLE order_contacts (
    order_id    UUID        PRIMARY KEY,
    customer_id UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

-- Deduplicação de eventos consumidos: a entrega do Kafka é at-least-once.
CREATE TABLE processed_events (
    event_id     UUID         PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL
);

COMMENT ON TABLE order_contacts IS 'A quem notificar sobre cada pedido, montado a partir de OrderCreated.';
