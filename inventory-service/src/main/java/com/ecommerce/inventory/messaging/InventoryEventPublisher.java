package com.ecommerce.inventory.messaging;

import com.ecommerce.contracts.StockRejectedEvent;
import com.ecommerce.contracts.StockReservedEvent;
import com.ecommerce.contracts.Topics;
import com.ecommerce.inventory.exception.InsufficientStockException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publica o desfecho da tentativa de reserva. */
@Component
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReserved(UUID orderId) {
        StockReservedEvent event = new StockReservedEvent(UUID.randomUUID(), orderId, Instant.now());
        // A chave é o pedido: assim todos os eventos do mesmo pedido caem na mesma
        // partição e são entregues em ordem.
        kafkaTemplate.send(Topics.STOCK_RESERVED, orderId.toString(), event);
        log.info("Publicado StockReserved para o pedido {}", orderId);
    }

    public void publishRejected(UUID orderId, String reason, List<StockRejectedEvent.Shortfall> shortfalls) {
        StockRejectedEvent event = new StockRejectedEvent(
                UUID.randomUUID(), orderId, reason, shortfalls, Instant.now());
        kafkaTemplate.send(Topics.STOCK_REJECTED, orderId.toString(), event);
        log.info("Publicado StockRejected para o pedido {}: {}", orderId, reason);
    }

    public void publishRejected(UUID orderId, InsufficientStockException ex) {
        publishRejected(orderId, ex.getMessage(), ex.getShortfalls().stream()
                .map(shortfall -> new StockRejectedEvent.Shortfall(
                        shortfall.productId(), shortfall.requested(), shortfall.available()))
                .toList());
    }
}
