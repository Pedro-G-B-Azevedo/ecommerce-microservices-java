package com.ecommerce.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecommerce.contracts.OrderCreatedEvent;
import com.ecommerce.contracts.StockRejectedEvent;
import com.ecommerce.contracts.StockReservedEvent;
import com.ecommerce.contracts.Topics;
import com.ecommerce.notification.dto.NotificationResponse;
import com.ecommerce.notification.entity.NotificationStatus;
import com.ecommerce.notification.entity.NotificationType;
import com.ecommerce.notification.service.NotificationService;
import com.ecommerce.notification.support.KafkaContainerSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;

/** Acompanha o ciclo completo de um pedido pela ótica das notificações. */
@SpringBootTest
class OrderEventsListenerTest extends KafkaContainerSupport {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private NotificationService notificationService;

    @Test
    @DisplayName("criação e confirmação geram duas notificações, na ordem")
    void notifiesOnCreationAndConfirmation() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        send(Topics.ORDER_CREATED, orderId, orderCreated(orderId, customerId));
        awaitNotifications(orderId, 1);

        send(Topics.STOCK_RESERVED, orderId, new StockReservedEvent(UUID.randomUUID(), orderId, Instant.now()));
        List<NotificationResponse> notifications = awaitNotifications(orderId, 2);

        assertThat(notifications).extracting(NotificationResponse::type)
                .containsExactlyInAnyOrder(NotificationType.ORDER_RECEIVED, NotificationType.ORDER_CONFIRMED);
        assertThat(notifications).allSatisfy(notification -> {
            assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.customerId()).isEqualTo(customerId);
            assertThat(notification.recipient()).isEqualTo("cliente-" + customerId + "@exemplo.com");
        });
    }

    @Test
    @DisplayName("rejeição gera uma notificação que detalha os itens em falta")
    void notifiesRejectionWithShortfalls() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        send(Topics.ORDER_CREATED, orderId, orderCreated(orderId, customerId));
        awaitNotifications(orderId, 1);

        send(Topics.STOCK_REJECTED, orderId, new StockRejectedEvent(UUID.randomUUID(), orderId,
                "Estoque insuficiente para 1 item(ns) do pedido",
                List.of(new StockRejectedEvent.Shortfall(productId, 99, 3)), Instant.now()));

        List<NotificationResponse> notifications = awaitNotifications(orderId, 2);
        assertThat(notifications)
                .filteredOn(n -> n.type() == NotificationType.ORDER_REJECTED)
                .singleElement()
                .satisfies(rejection -> assertThat(rejection.body())
                        .contains("Estoque insuficiente")
                        .contains("pedidas 99, disponíveis 3"));
    }

    @Test
    @DisplayName("o mesmo evento entregue duas vezes não notifica o cliente duas vezes")
    void doesNotNotifyTwiceForRedelivery() {
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = orderCreated(orderId, UUID.randomUUID());

        send(Topics.ORDER_CREATED, orderId, event);
        awaitNotifications(orderId, 1);

        send(Topics.ORDER_CREATED, orderId, event);

        // Espera um intervalo sem que uma segunda notificação apareça.
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(notifications(orderId)).hasSize(1));
    }

    private void send(String topic, UUID orderId, Object event) {
        kafkaTemplate.send(topic, orderId.toString(), event);
    }

    private List<NotificationResponse> awaitNotifications(UUID orderId, int expected) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(notifications(orderId)).hasSize(expected));
        return notifications(orderId);
    }

    private List<NotificationResponse> notifications(UUID orderId) {
        // Consulta como ADMIN: o teste precisa enxergar as notificações de qualquer cliente.
        return notificationService.search(orderId, null, null, PageRequest.of(0, 20),
                UUID.randomUUID(), true).content();
    }

    private static OrderCreatedEvent orderCreated(UUID orderId, UUID customerId) {
        return new OrderCreatedEvent(UUID.randomUUID(), orderId, customerId,
                List.of(new OrderCreatedEvent.OrderLine(UUID.randomUUID(), 2)),
                new BigDecimal("299.80"), Instant.now());
    }
}
