package com.ecommerce.notification.service;

import com.ecommerce.contracts.OrderCreatedEvent;
import com.ecommerce.contracts.StockRejectedEvent;
import com.ecommerce.contracts.StockReservedEvent;
import com.ecommerce.notification.dto.NotificationResponse;
import com.ecommerce.notification.dto.PageResponse;
import com.ecommerce.notification.entity.Notification;
import com.ecommerce.notification.entity.NotificationStatus;
import com.ecommerce.notification.entity.OrderContact;
import com.ecommerce.notification.entity.ProcessedEvent;
import com.ecommerce.notification.exception.OrderContactNotFoundException;
import com.ecommerce.notification.repository.NotificationRepository;
import com.ecommerce.notification.repository.NotificationSpecifications;
import com.ecommerce.notification.repository.OrderContactRepository;
import com.ecommerce.notification.repository.ProcessedEventRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final OrderContactRepository orderContactRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationComposer composer;
    private final NotificationSender sender;

    public NotificationService(NotificationRepository notificationRepository,
                               OrderContactRepository orderContactRepository,
                               ProcessedEventRepository processedEventRepository,
                               NotificationComposer composer,
                               NotificationSender sender) {
        this.notificationRepository = notificationRepository;
        this.orderContactRepository = orderContactRepository;
        this.processedEventRepository = processedEventRepository;
        this.composer = composer;
        this.sender = sender;
    }

    @Transactional
    public void onOrderCreated(OrderCreatedEvent event) {
        if (alreadyProcessed(event.eventId(), "OrderCreated")) {
            return;
        }
        // Guarda o destinatário agora: os eventos de estoque não trazem o cliente.
        orderContactRepository.save(new OrderContact(event.orderId(), event.customerId()));
        deliver(composer.orderReceived(event.orderId(), event.customerId(), event.totalAmount()));
    }

    @Transactional
    public void onStockReserved(StockReservedEvent event) {
        if (alreadyProcessed(event.eventId(), "StockReserved")) {
            return;
        }
        UUID customerId = requireContact(event.orderId());
        deliver(composer.orderConfirmed(event.orderId(), customerId));
    }

    @Transactional
    public void onStockRejected(StockRejectedEvent event) {
        if (alreadyProcessed(event.eventId(), "StockRejected")) {
            return;
        }
        UUID customerId = requireContact(event.orderId());
        deliver(composer.orderRejected(event.orderId(), customerId, event.reason(), event.shortfalls()));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> search(UUID orderId, UUID customerId,
                                                     NotificationStatus status, Pageable pageable,
                                                     UUID requesterId, boolean requesterIsAdmin) {
        List<Specification<Notification>> filters = new ArrayList<>();
        if (orderId != null) {
            filters.add(NotificationSpecifications.hasOrderId(orderId));
        }

        // Um cliente só enxerga as próprias notificações: o filtro é imposto, não
        // aceito da requisição.
        UUID effectiveCustomerId = requesterIsAdmin ? customerId : requesterId;
        if (effectiveCustomerId != null) {
            filters.add(NotificationSpecifications.hasCustomerId(effectiveCustomerId));
        }
        if (status != null) {
            filters.add(NotificationSpecifications.hasStatus(status));
        }

        Page<Notification> page = notificationRepository.findAll(Specification.allOf(filters), pageable);
        return PageResponse.from(page, NotificationResponse::from);
    }

    /**
     * Entrega a notificação e grava o resultado.
     *
     * <p>Uma falha no envio não derruba o consumo: a notificação é registrada como
     * {@code FAILED} e o evento segue processado. Deixar a exceção subir faria o
     * Kafka reentregar o evento e reenviar a mensagem para quem já a recebeu — pior
     * do que registrar a falha e seguir.
     */
    private void deliver(Notification notification) {
        try {
            sender.send(notification);
            notification.markSent();
        } catch (RuntimeException ex) {
            log.error("Falha ao enviar a notificação do pedido {}", notification.getOrderId(), ex);
            notification.markFailed(ex.getMessage());
        }
        notificationRepository.save(notification);
    }

    private UUID requireContact(UUID orderId) {
        return orderContactRepository.findById(orderId)
                .map(OrderContact::getCustomerId)
                .orElseThrow(() -> new OrderContactNotFoundException(orderId));
    }

    private boolean alreadyProcessed(UUID eventId, String eventType) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Evento {} já havia sido processado; nenhuma notificação será reenviada", eventId);
            return true;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, eventType));
        return false;
    }
}
