-- Motivo da rejeição vindo do inventory-service, para que o cliente saiba o que faltou.
ALTER TABLE orders ADD COLUMN rejection_reason VARCHAR(500);

-- Deduplicação de eventos consumidos.
--
-- A entrega do Kafka é at-least-once: o mesmo evento pode chegar duas vezes, por
-- exemplo se o consumidor cair depois de processar e antes de confirmar o offset.
-- Registrar o eventId em uma tabela com chave primária faz a segunda tentativa
-- falhar na inserção, e o consumidor sabe que pode ignorá-la.
CREATE TABLE processed_events (
    event_id     UUID        PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE processed_events IS 'Eventos já consumidos, para tornar o consumo idempotente.';
