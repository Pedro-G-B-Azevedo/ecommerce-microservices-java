package com.ecommerce.notification.messaging;

import com.ecommerce.contracts.OrderCreatedEvent;
import com.ecommerce.contracts.StockRejectedEvent;
import com.ecommerce.contracts.StockReservedEvent;
import com.ecommerce.contracts.Topics;
import com.ecommerce.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Acompanha a vida do pedido e avisa o cliente a cada desfecho.
 *
 * <p>O notification-service é um consumidor puro: não participa da saga nem
 * influencia o resultado do pedido. Por isso consome os mesmos tópicos em um grupo
 * próprio, e uma falha aqui não afeta orders nem inventory.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final NotificationService notificationService;

    public OrderEventsListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Recebido OrderCreated {} para o pedido {}", event.eventId(), event.orderId());
        notificationService.onOrderCreated(event);
    }

    @KafkaListener(topics = Topics.STOCK_RESERVED, groupId = "${spring.kafka.consumer.group-id}")
    public void onStockReserved(StockReservedEvent event) {
        log.info("Recebido StockReserved {} para o pedido {}", event.eventId(), event.orderId());
        notificationService.onStockReserved(event);
    }

    @KafkaListener(topics = Topics.STOCK_REJECTED, groupId = "${spring.kafka.consumer.group-id}")
    public void onStockRejected(StockRejectedEvent event) {
        log.info("Recebido StockRejected {} para o pedido {}", event.eventId(), event.orderId());
        notificationService.onStockRejected(event);
    }
}
