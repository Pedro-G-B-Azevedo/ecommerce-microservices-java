package com.ecommerce.contracts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Não foi possível separar estoque para o pedido.
 *
 * <p>Carrega a lista completa de faltas para que o orders-service possa explicar ao
 * cliente o que faltou, sem precisar consultar o inventory de volta.
 */
public record StockRejectedEvent(
        UUID eventId,
        UUID orderId,
        String reason,
        List<Shortfall> shortfalls,
        Instant occurredAt) {

    public record Shortfall(UUID productId, int requested, int available) {
    }
}
