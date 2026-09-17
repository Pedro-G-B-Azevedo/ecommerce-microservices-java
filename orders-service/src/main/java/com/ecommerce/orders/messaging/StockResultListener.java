package com.ecommerce.orders.messaging;

import com.ecommerce.contracts.StockRejectedEvent;
import com.ecommerce.contracts.StockReservedEvent;
import com.ecommerce.contracts.Topics;
import com.ecommerce.orders.observability.CorrelationId;
import com.ecommerce.orders.service.OrderSagaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
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
    public void onStockReserved(StockReservedEvent event,
                                @Header(value = CorrelationId.HEADER, required = false) byte[] correlationIdHeader) {
        // Do header ao MDC: o mesmo id que o inventory-service usou ao publicar
        // este evento passa a valer aqui — inclusive na eventual chamada de
        // compensação ao inventory-service, se o pedido já tiver sido cancelado.
        MDC.put(CorrelationId.MDC_KEY, CorrelationId.decode(correlationIdHeader));
        try {
            log.info("Recebido StockReserved {} para o pedido {}", event.eventId(), event.orderId());
            sagaService.confirm(event.eventId(), event.orderId());
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    @KafkaListener(topics = Topics.STOCK_REJECTED, groupId = "${spring.kafka.consumer.group-id}")
    public void onStockRejected(StockRejectedEvent event,
                                @Header(value = CorrelationId.HEADER, required = false) byte[] correlationIdHeader) {
        MDC.put(CorrelationId.MDC_KEY, CorrelationId.decode(correlationIdHeader));
        try {
            log.info("Recebido StockRejected {} para o pedido {}", event.eventId(), event.orderId());
            sagaService.reject(event.eventId(), event.orderId(), event.reason());
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
