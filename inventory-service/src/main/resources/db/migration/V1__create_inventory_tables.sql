CREATE TABLE products (
    id          UUID           PRIMARY KEY,
    sku         VARCHAR(50)    NOT NULL,
    name        VARCHAR(200)   NOT NULL,
    description TEXT,
    price       NUMERIC(19, 2) NOT NULL,
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL,
    version     BIGINT         NOT NULL,
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_price_non_negative CHECK (price >= 0)
);

CREATE INDEX ix_products_active ON products (active);

-- Estoque em tabela própria: catálogo e disponibilidade mudam por motivos
-- diferentes e com frequências diferentes. Separá-los também deixa o bloqueio
-- pessimista da reserva incidir só sobre a linha de estoque, sem travar leituras
-- do catálogo.
CREATE TABLE stock_items (
    product_id         UUID        PRIMARY KEY,
    available_quantity INTEGER     NOT NULL,
    reserved_quantity  INTEGER     NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT      NOT NULL,
    CONSTRAINT fk_stock_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_stock_items_available_non_negative CHECK (available_quantity >= 0),
    CONSTRAINT ck_stock_items_reserved_non_negative CHECK (reserved_quantity >= 0)
);

CREATE TABLE stock_reservations (
    id         UUID        PRIMARY KEY,
    order_id   UUID        NOT NULL,
    product_id UUID        NOT NULL,
    quantity   INTEGER     NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL,
    CONSTRAINT fk_stock_reservations_product FOREIGN KEY (product_id) REFERENCES products (id),
    -- Chave natural da idempotência: reprocessar o mesmo evento de pedido não
    -- pode reservar estoque duas vezes.
    CONSTRAINT uk_stock_reservations_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_stock_reservations_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX ix_stock_reservations_order_id ON stock_reservations (order_id);

COMMENT ON TABLE stock_reservations IS 'Reservas de estoque por pedido, usadas pela saga de confirmação.';
COMMENT ON COLUMN stock_items.reserved_quantity IS 'Já retirado de available, aguardando confirmação ou liberação.';
