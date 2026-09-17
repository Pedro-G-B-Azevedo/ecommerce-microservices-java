package com.ecommerce.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecommerce.contracts.OrderCreatedEvent;
import com.ecommerce.contracts.StockRejectedEvent;
import com.ecommerce.contracts.StockReservedEvent;
import com.ecommerce.notification.entity.Notification;
import com.ecommerce.notification.entity.NotificationStatus;
import com.ecommerce.notification.entity.NotificationType;
import com.ecommerce.notification.entity.OrderContact;
import com.ecommerce.notification.entity.ProcessedEvent;
import com.ecommerce.notification.exception.OrderContactNotFoundException;
import com.ecommerce.notification.repository.NotificationRepository;
import com.ecommerce.notification.repository.OrderContactRepository;
import com.ecommerce.notification.repository.ProcessedEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private OrderContactRepository orderContactRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationSender sender;

    private NotificationService service;

    private NotificationService service() {
        if (service == null) {
            service = new NotificationService(notificationRepository, orderContactRepository,
                    processedEventRepository, new NotificationComposer(), sender);
        }
        return service;
    }

    @Test
    @DisplayName("pedido criado: guarda o contato e avisa que o pedido foi recebido")
    void ordersCreatedStoresContactAndNotifies() {
        when(processedEventRepository.existsById(EVENT_ID)).thenReturn(false);

        service().onOrderCreated(orderCreated());

        ArgumentCaptor<OrderContact> contact = ArgumentCaptor.forClass(OrderContact.class);
        verify(orderContactRepository).save(contact.capture());
        assertThat(contact.getValue().getCustomerId()).isEqualTo(CUSTOMER_ID);

        Notification saved = captureSaved();
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_RECEIVED);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getRecipient()).isEqualTo("cliente-" + CUSTOMER_ID + "@exemplo.com");
        assertThat(saved.getBody()).contains(ORDER_ID.toString()).contains("149.90");
        assertThat(saved.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("estoque reservado: usa o contato guardado para avisar da confirmação")
    void stockReservedNotifiesConfirmation() {
        when(processedEventRepository.existsById(EVENT_ID)).thenReturn(false);
        when(orderContactRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(new OrderContact(ORDER_ID, CUSTOMER_ID)));

        service().onStockReserved(new StockReservedEvent(EVENT_ID, ORDER_ID, Instant.now()));

        Notification saved = captureSaved();
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CONFIRMED);
        assertThat(saved.getCustomerId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    @DisplayName("estoque rejeitado: a mensagem detalha cada item em falta")
    void stockRejectedListsShortfalls() {
        UUID productId = UUID.randomUUID();
        when(processedEventRepository.existsById(EVENT_ID)).thenReturn(false);
        when(orderContactRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(new OrderContact(ORDER_ID, CUSTOMER_ID)));

        service().onStockRejected(new StockRejectedEvent(EVENT_ID, ORDER_ID, "Estoque insuficiente",
                List.of(new StockRejectedEvent.Shortfall(productId, 10, 2)), Instant.now()));

        Notification saved = captureSaved();
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_REJECTED);
        assertThat(saved.getBody())
                .contains("Estoque insuficiente")
                .contains(productId.toString())
                .contains("pedidas 10, disponíveis 2");
    }

    @Test
    @DisplayName("desfecho sem OrderCreated visto antes falha, para que o Kafka reentregue")
    void throwsWhenContactIsUnknown() {
        when(processedEventRepository.existsById(EVENT_ID)).thenReturn(false);
        when(orderContactRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().onStockReserved(
                new StockReservedEvent(EVENT_ID, ORDER_ID, Instant.now())))
                .isInstanceOf(OrderContactNotFoundException.class);

        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("evento repetido não gera uma segunda mensagem ao cliente")
    void doesNotNotifyTwiceForTheSameEvent() {
        when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

        service().onOrderCreated(orderCreated());

        verifyNoInteractions(sender);
        verify(notificationRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("falha no envio registra FAILED em vez de derrubar o consumo")
    void recordsFailureInsteadOfPropagating() {
        when(processedEventRepository.existsById(EVENT_ID)).thenReturn(false);
        doThrow(new IllegalStateException("provedor fora do ar")).when(sender).send(any());

        service().onOrderCreated(orderCreated());

        Notification saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.getFailureReason()).isEqualTo("provedor fora do ar");
        assertThat(saved.getSentAt()).isNull();
    }

    private Notification captureSaved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    private static OrderCreatedEvent orderCreated() {
        return new OrderCreatedEvent(EVENT_ID, ORDER_ID, CUSTOMER_ID,
                List.of(new OrderCreatedEvent.OrderLine(UUID.randomUUID(), 1)),
                new BigDecimal("149.90"), Instant.now());
    }
}
