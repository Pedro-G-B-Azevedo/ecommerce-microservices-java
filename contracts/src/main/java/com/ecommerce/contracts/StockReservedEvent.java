package com.ecommerce.contracts;

import java.time.Instant;
import java.util.UUID;

/** Estoque separado com sucesso para o pedido. */
public record StockReservedEvent(
        UUID eventId,
        UUID orderId,
        Instant occurredAt) {
}
