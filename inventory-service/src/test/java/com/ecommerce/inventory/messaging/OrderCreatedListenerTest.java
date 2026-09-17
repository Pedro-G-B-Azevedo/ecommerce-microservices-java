package com.ecommerce.inventory.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecommerce.contracts.OrderCreatedEvent;
import com.ecommerce.contracts.StockRejectedEvent;
import com.ecommerce.contracts.StockReservedEvent;
import com.ecommerce.contracts.Topics;
import com.ecommerce.inventory.dto.CreateProductRequest;
import com.ecommerce.inventory.dto.ProductResponse;
import com.ecommerce.inventory.service.ProductService;
import com.ecommerce.inventory.support.KafkaContainerSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

/**
 * Percorre o lado do inventory na saga: recebe {@code OrderCreated} do Kafka,
 * separa o estoque e publica o desfecho.
 */
@SpringBootTest
class OrderCreatedListenerTest extends KafkaContainerSupport {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ProductService productService;

    private Consumer<String, Object> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                KAFKA.getBootstrapServers(), "teste-" + UUID.randomUUID(), "true");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.springframework.kafka.support.serializer.JsonDeserializer");
        props.put("spring.json.trusted.packages", "com.ecommerce.contracts");
        props.put("auto.offset.reset", "earliest");
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(Topics.STOCK_RESERVED, Topics.STOCK_REJECTED));
        consumer.poll(Duration.ofMillis(500));
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    @DisplayName("estoque suficiente: separa as unidades e publica StockReserved")
    void reservesAndPublishesReserved() {
        ProductResponse product = newProduct(10);
        UUID orderId = UUID.randomUUID();

        kafkaTemplate.send(Topics.ORDER_CREATED, orderId.toString(), orderCreated(orderId, product.id(), 3));

        ConsumerRecord<String, Object> record = awaitRecord();
        assertThat(record.topic()).isEqualTo(Topics.STOCK_RESERVED);
        assertThat(record.value()).isInstanceOf(StockReservedEvent.class);
        assertThat(((StockReservedEvent) record.value()).orderId()).isEqualTo(orderId);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var stock = productService.findStock(product.id());
            assertThat(stock.availableQuantity()).isEqualTo(7);
            assertThat(stock.reservedQuantity()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("estoque insuficiente: publica StockRejected com as faltas e não toca no estoque")
    void publishesRejectedWithoutTouchingStock() {
        ProductResponse product = newProduct(2);
        UUID orderId = UUID.randomUUID();

        kafkaTemplate.send(Topics.ORDER_CREATED, orderId.toString(), orderCreated(orderId, product.id(), 99));

        ConsumerRecord<String, Object> record = awaitRecord();
        assertThat(record.topic()).isEqualTo(Topics.STOCK_REJECTED);
        StockRejectedEvent event = (StockRejectedEvent) record.value();
        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.shortfalls()).singleElement().satisfies(shortfall -> {
            assertThat(shortfall.productId()).isEqualTo(product.id());
            assertThat(shortfall.requested()).isEqualTo(99);
            assertThat(shortfall.available()).isEqualTo(2);
        });

        var stock = productService.findStock(product.id());
        assertThat(stock.availableQuantity()).isEqualTo(2);
        assertThat(stock.reservedQuantity()).isZero();
    }

    @Test
    @DisplayName("o mesmo evento entregue duas vezes reserva estoque uma vez só")
    void redeliveryReservesOnlyOnce() {
        ProductResponse product = newProduct(10);
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = orderCreated(orderId, product.id(), 4);

        kafkaTemplate.send(Topics.ORDER_CREATED, orderId.toString(), event);
        awaitRecord();

        // Mesmo evento, reentregue como aconteceria numa falha entre processar e
        // confirmar o offset.
        kafkaTemplate.send(Topics.ORDER_CREATED, orderId.toString(), event);
        awaitRecord();

        var stock = productService.findStock(product.id());
        assertThat(stock.availableQuantity()).isEqualTo(6);
        assertThat(stock.reservedQuantity()).isEqualTo(4);
    }

    private ConsumerRecord<String, Object> awaitRecord() {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new AssertionError("Nenhum evento de desfecho foi publicado dentro do tempo esperado");
    }

    private ProductResponse newProduct(int quantity) {
        return productService.create(new CreateProductRequest(
                "SAGA-" + UUID.randomUUID(), "Produto da saga", null, new BigDecimal("10.00"), quantity));
    }

    private static OrderCreatedEvent orderCreated(UUID orderId, UUID productId, int quantity) {
        return new OrderCreatedEvent(
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                List.of(new OrderCreatedEvent.OrderLine(productId, quantity)),
                new BigDecimal("10.00").multiply(BigDecimal.valueOf(quantity)),
                Instant.now());
    }
}
