package com.ecommerce.orders.messaging;

import com.ecommerce.contracts.OrderCreatedEvent;

/**
 * Evento interno do Spring, disparado dentro da transação que grava o pedido.
 *
 * <p>Existe para adiar a publicação no Kafka até depois do commit: publicar dentro
 * da transação anunciaria um pedido que ainda pode não ser gravado.
 */
public record OrderCreatedDomainEvent(OrderCreatedEvent payload) {
}
