package com.ecommerce.orders.service;

import com.ecommerce.orders.entity.Order;
import com.ecommerce.orders.entity.OrderStatus;
import com.ecommerce.orders.entity.ProcessedEvent;
import com.ecommerce.orders.exception.OrderNotFoundException;
import com.ecommerce.orders.repository.OrderRepository;
import com.ecommerce.orders.repository.ProcessedEventRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conduz o desfecho da saga a partir da resposta do inventory-service.
 *
 * <p>Cada método é idempotente por {@code eventId}: a entrega do Kafka é
 * at-least-once, então o mesmo evento pode chegar mais de uma vez.
 */
@Service
public class OrderSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaService.class);

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ReservationCompensation compensation;

    public OrderSagaService(OrderRepository orderRepository,
                            ProcessedEventRepository processedEventRepository,
                            ReservationCompensation compensation) {
        this.orderRepository = orderRepository;
        this.processedEventRepository = processedEventRepository;
        this.compensation = compensation;
    }

    @Transactional
    public void confirm(UUID eventId, UUID orderId) {
        if (alreadyProcessed(eventId, "StockReserved")) {
            return;
        }

        Order order = requireOrder(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            // O cliente cancelou enquanto o estoque estava sendo separado. O pedido
            // não volta atrás: o que se desfaz é a reserva.
            log.warn("Pedido {} foi cancelado antes da confirmação; liberando a reserva", orderId);
            compensation.releaseReservation(orderId);
            return;
        }

        order.confirm();
        log.info("Pedido {} confirmado", orderId);
    }

    @Transactional
    public void reject(UUID eventId, UUID orderId, String reason) {
        if (alreadyProcessed(eventId, "StockRejected")) {
            return;
        }

        Order order = requireOrder(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            // Nada a compensar: a reserva não chegou a existir.
            log.info("Pedido {} já estava cancelado quando a rejeição chegou", orderId);
            return;
        }

        order.reject(reason);
        log.info("Pedido {} rejeitado: {}", orderId, reason);
    }

    /**
     * Registra o evento e diz se ele já havia sido processado.
     *
     * <p>A gravação acontece na mesma transação da mudança de status: ou as duas
     * coisas valem, ou nenhuma. Se o processamento falhar e a transação voltar
     * atrás, a marca some junto e o reprocessamento é permitido.
     */
    private boolean alreadyProcessed(UUID eventId, String eventType) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Evento {} já havia sido processado; ignorando repetição", eventId);
            return true;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, eventType));
        return false;
    }

    private Order requireOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
