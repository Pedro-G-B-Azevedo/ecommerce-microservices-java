CREATE TABLE orders (
    id           UUID           PRIMARY KEY,
    customer_id  UUID           NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL,
    version      BIGINT         NOT NULL,
    CONSTRAINT ck_orders_total_amount_non_negative CHECK (total_amount >= 0)
);

CREATE INDEX ix_orders_customer_id ON orders (customer_id);
CREATE INDEX ix_orders_status ON orders (status);
CREATE INDEX ix_orders_created_at ON orders (created_at DESC);

CREATE TABLE order_items (
    id         UUID           PRIMARY KEY,
    order_id   UUID           NOT NULL,
    product_id UUID           NOT NULL,
    quantity   INTEGER        NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price_non_negative CHECK (unit_price >= 0)
);

CREATE INDEX ix_order_items_order_id ON order_items (order_id);

COMMENT ON TABLE orders IS 'Pedidos. O status é conduzido pela saga de reserva de estoque.';
COMMENT ON COLUMN orders.version IS 'Bloqueio otimista: o retorno da saga concorre com operações do cliente.';
