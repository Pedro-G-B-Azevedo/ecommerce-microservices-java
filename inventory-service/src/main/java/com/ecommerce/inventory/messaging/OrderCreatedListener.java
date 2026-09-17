package com.ecommerce.inventory.messaging;

import com.ecommerce.contracts.OrderCreatedEvent;
import com.ecommerce.contracts.Topics;
import com.ecommerce.inventory.dto.ReservationItemRequest;
import com.ecommerce.inventory.dto.ReserveStockRequest;
import com.ecommerce.inventory.exception.InsufficientStockException;
import com.ecommerce.inventory.exception.ProductNotFoundException;
import com.ecommerce.inventory.observability.CorrelationId;
import com.ecommerce.inventory.service.StockReservationService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Reage à criação de um pedido tentando separar o estoque.
 *
 * <p>A reserva em si é idempotente por {@code orderId}, então a reentrega do mesmo
 * evento — que a entrega <em>at-least-once</em> do Kafka torna esperada — não
 * separa estoque duas vezes.
 */
@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final StockReservationService reservationService;
    private final InventoryEventPublisher publisher;

    public OrderCreatedListener(StockReservationService reservationService,
                                InventoryEventPublisher publisher) {
        this.reservationService = reservationService;
        this.publisher = publisher;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderCreated(OrderCreatedEvent event,
                               @Header(value = CorrelationId.HEADER, required = false) byte[] correlationIdHeader) {
        // Do header ao MDC: o id nascido no orders-service passa a valer para
        // todo log desta thread, inclusive o que o publisher emite mais abaixo.
        MDC.put(CorrelationId.MDC_KEY, CorrelationId.decode(correlationIdHeader));
        try {
            log.info("Recebido OrderCreated {} para o pedido {}", event.eventId(), event.orderId());

            try {
                reservationService.reserve(toReservationRequest(event));
                publisher.publishReserved(event.orderId());

            } catch (InsufficientStockException ex) {
                // Desfecho de negócio, não erro: o pedido não pode ser atendido, e quem
                // decide o que fazer com isso é o orders-service. Reprocessar não mudaria
                // nada, então a mensagem não vai para o DLT.
                publisher.publishRejected(event.orderId(), ex);

            } catch (ProductNotFoundException ex) {
                // Produto sumiu do catálogo entre a criação do pedido e a reserva.
                publisher.publishRejected(event.orderId(), ex.getMessage(), List.of());
            }
            // Qualquer outra exceção sobe: é falha técnica, e aí sim vale repetir e,
            // se persistir, mandar para o dead-letter topic.
        } finally {
            // Threads de consumidor são reaproveitadas entre mensagens.
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    private static ReserveStockRequest toReservationRequest(OrderCreatedEvent event) {
        return new ReserveStockRequest(
                event.orderId(),
                event.items().stream()
                        .map(line -> new ReservationItemRequest(line.productId(), line.quantity()))
                        .toList());
    }
}
