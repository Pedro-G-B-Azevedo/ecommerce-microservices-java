package com.ecommerce.orders.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.ecommerce.contracts.OrderCreatedEvent;
import com.ecommerce.contracts.StockRejectedEvent;
import com.ecommerce.contracts.StockReservedEvent;
import com.ecommerce.contracts.Topics;
import com.ecommerce.orders.client.InventoryClient;
import com.ecommerce.orders.client.ProductSnapshot;
import com.ecommerce.orders.dto.CreateOrderRequest;
import com.ecommerce.orders.dto.OrderItemRequest;
import com.ecommerce.orders.dto.OrderResponse;
import com.ecommerce.orders.entity.OrderStatus;
import com.ecommerce.orders.service.CurrentUser;
import com.ecommerce.orders.service.OrderService;
import com.ecommerce.orders.support.KafkaContainerSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Percorre o lado do orders na saga: publica {@code OrderCreated} ao criar o pedido
 * e reage ao desfecho que o inventory-service devolve.
 */
@SpringBootTest
class OrderSagaFlowTest extends KafkaContainerSupport {

    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Autowired
    private OrderService orderService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    private InventoryClient inventoryClient;

    private Consumer<String, Object> consumer;

    @BeforeEach
    void setUp() {
        when(inventoryClient.findByIds(anyList())).thenReturn(List.of(
                new ProductSnapshot(PRODUCT_ID, "TEC-001", "Teclado", new BigDecimal("100.00"), true)));

        Map<String, Object> props = KafkaTestUtils.consumerProps(
                KAFKA.getBootstrapServers(), "teste-" + UUID.randomUUID(), "true");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.springframework.kafka.support.serializer.JsonDeserializer");
        props.put("spring.json.trusted.packages", "com.ecommerce.contracts");
        props.put("auto.offset.reset", "earliest");
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(Topics.ORDER_CREATED));
        consumer.poll(Duration.ofMillis(500));
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    @DisplayName("criar um pedido publica OrderCreated depois do commit")
    void publishesOrderCreatedAfterCommit() {
        OrderResponse order = createOrder();

        ConsumerRecord<String, Object> record = awaitOrderCreated();
        assertThat(record.key()).isEqualTo(order.id().toString());
        OrderCreatedEvent event = (OrderCreatedEvent) record.value();
        assertThat(event.orderId()).isEqualTo(order.id());
        assertThat(event.items()).singleElement()
                .satisfies(line -> assertThat(line.productId()).isEqualTo(PRODUCT_ID));
    }

    @Test
    @DisplayName("StockReserved leva o pedido de PENDING para CONFIRMED")
    void stockReservedConfirmsOrder() {
        OrderResponse order = createOrder();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);

        kafkaTemplate.send(Topics.STOCK_RESERVED, order.id().toString(),
                new StockReservedEvent(UUID.randomUUID(), order.id(), Instant.now()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(reload(order.id()).status()).isEqualTo(OrderStatus.CONFIRMED));
    }

    @Test
    @DisplayName("StockRejected leva o pedido para REJECTED e guarda o motivo")
    void stockRejectedRejectsOrder() {
        OrderResponse order = createOrder();

        kafkaTemplate.send(Topics.STOCK_REJECTED, order.id().toString(),
                new StockRejectedEvent(UUID.randomUUID(), order.id(), "Estoque insuficiente",
                        List.of(new StockRejectedEvent.Shortfall(PRODUCT_ID, 10, 2)), Instant.now()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            OrderResponse updated = reload(order.id());
            assertThat(updated.status()).isEqualTo(OrderStatus.REJECTED);
            assertThat(updated.rejectionReason()).isEqualTo("Estoque insuficiente");
        });
    }

    @Test
    @DisplayName("o mesmo evento entregue duas vezes não muda o pedido na segunda")
    void redeliveredEventIsIgnored() {
        OrderResponse order = createOrder();
        StockReservedEvent event = new StockReservedEvent(UUID.randomUUID(), order.id(), Instant.now());

        kafkaTemplate.send(Topics.STOCK_RESERVED, order.id().toString(), event);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(reload(order.id()).status()).isEqualTo(OrderStatus.CONFIRMED));

        // Mesmo eventId: a deduplicação por processed_events deve descartá-lo.
        kafkaTemplate.send(Topics.STOCK_RESERVED, order.id().toString(), event);

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(reload(order.id()).status()).isEqualTo(OrderStatus.CONFIRMED));
    }

    private OrderResponse createOrder() {
        CurrentUser cliente = new CurrentUser(UUID.randomUUID(), Set.of("CLIENTE"));
        return orderService.create(
                new CreateOrderRequest(List.of(new OrderItemRequest(PRODUCT_ID, 1))), cliente);
    }

    private OrderResponse reload(UUID orderId) {
        // ADMIN para poder reler qualquer pedido, independentemente de quem o criou.
        return orderService.findById(orderId, new CurrentUser(UUID.randomUUID(), Set.of("ADMIN")));
    }

    private ConsumerRecord<String, Object> awaitOrderCreated() {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new AssertionError("OrderCreated não foi publicado dentro do tempo esperado");
    }
}
