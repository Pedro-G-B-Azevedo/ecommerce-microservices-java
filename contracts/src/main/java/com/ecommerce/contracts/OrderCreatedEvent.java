package com.ecommerce.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido criado, aguardando reserva de estoque.
 *
 * <p>{@code eventId} é a chave de deduplicação: a entrega é <em>at-least-once</em>,
 * então o mesmo evento pode chegar mais de uma vez ao consumidor.
 *
 * <p>O evento não carrega preço: o inventory-service precisa saber o que separar,
 * não quanto custa.
 */
public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        List<OrderLine> items,
        BigDecimal totalAmount,
        Instant occurredAt) {

    public record OrderLine(UUID productId, int quantity) {
    }
}
