package com.ecommerce.orders.messaging;

import com.ecommerce.contracts.StockRejectedEvent;
import com.ecommerce.contracts.StockReservedEvent;
import com.ecommerce.contracts.Topics;
import com.ecommerce.orders.service.OrderSagaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Fecha a saga com a resposta do inventory-service. */
@Component
public class StockResultListener {

    private static final Logger log = LoggerFactory.getLogger(StockResultListener.class);

    private final OrderSagaService sagaService;

    public StockResultListener(OrderSagaService sagaService) {
        this.sagaService = sagaService;
    }

    @KafkaListener(topics = Topics.STOCK_RESERVED, groupId = "${spring.kafka.consumer.group-id}")
    public void onStockReserved(StockReservedEvent event) {
        log.info("Recebido StockReserved {} para o pedido {}", event.eventId(), event.orderId());
        sagaService.confirm(event.eventId(), event.orderId());
    }

    @KafkaListener(topics = Topics.STOCK_REJECTED, groupId = "${spring.kafka.consumer.group-id}")
    public void onStockRejected(StockRejectedEvent event) {
        log.info("Recebido StockRejected {} para o pedido {}", event.eventId(), event.orderId());
        sagaService.reject(event.eventId(), event.orderId(), event.reason());
    }
}
