package com.ecommerce.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.contracts.OrderCreatedEvent;
import com.ecommerce.orders.client.InventoryClient;
import com.ecommerce.orders.client.ProductSnapshot;
import com.ecommerce.orders.dto.CreateOrderRequest;
import com.ecommerce.orders.dto.OrderItemRequest;
import com.ecommerce.orders.dto.OrderResponse;
import com.ecommerce.orders.entity.Order;
import com.ecommerce.orders.entity.OrderItem;
import com.ecommerce.orders.entity.OrderStatus;
import com.ecommerce.orders.exception.DuplicateOrderItemException;
import com.ecommerce.orders.exception.InvalidOrderStateException;
import com.ecommerce.orders.exception.InventoryUnavailableException;
import com.ecommerce.orders.exception.OrderNotFoundException;
import com.ecommerce.orders.exception.ProductUnavailableException;
import com.ecommerce.orders.messaging.OrderCreatedDomainEvent;
import com.ecommerce.orders.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final CurrentUser CLIENTE = new CurrentUser(CUSTOMER_ID, Set.of("CLIENTE"));

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    @Nested
    class Create {

        @Test
        @DisplayName("usa o preço do catálogo e soma os subtotais no total do pedido")
        void pricesFromCatalogAndSumsTotal() {
            UUID keyboard = UUID.randomUUID();
            UUID mouse = UUID.randomUUID();
            when(inventoryClient.findByIds(anyList())).thenReturn(List.of(
                    product(keyboard, "149.90"),
                    product(mouse, "59.00")));
            when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

            OrderResponse response = orderService.create(new CreateOrderRequest(List.of(
                    new OrderItemRequest(keyboard, 2),
                    new OrderItemRequest(mouse, 1))), CLIENTE);

            // 2 x 149.90 + 1 x 59.00
            assertThat(response.totalAmount()).isEqualByComparingTo("358.80");
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.items()).hasSize(2);
        }

        @Test
        @DisplayName("o preço gravado é o do catálogo, mesmo que o cliente tente outro")
        void ignoresAnyClientSuppliedPrice() {
            UUID productId = UUID.randomUUID();
            when(inventoryClient.findByIds(anyList())).thenReturn(List.of(product(productId, "349.90")));
            when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

            OrderResponse response = orderService.create(new CreateOrderRequest(List.of(new OrderItemRequest(productId, 3))), CLIENTE);

            assertThat(response.items()).singleElement().satisfies(item -> {
                assertThat(item.unitPrice()).isEqualByComparingTo("349.90");
                assertThat(item.subtotal()).isEqualByComparingTo("1049.70");
            });
        }

        @Test
        @DisplayName("dispara o evento de domínio que leva o pedido ao Kafka após o commit")
        void publishesDomainEvent() {
            UUID productId = UUID.randomUUID();
            when(inventoryClient.findByIds(anyList())).thenReturn(List.of(product(productId, "10.00")));
            when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

            OrderResponse response = orderService.create(new CreateOrderRequest(List.of(new OrderItemRequest(productId, 4))), CLIENTE);

            ArgumentCaptor<OrderCreatedDomainEvent> captor =
                    ArgumentCaptor.forClass(OrderCreatedDomainEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            OrderCreatedEvent payload = captor.getValue().payload();
            assertThat(payload.orderId()).isEqualTo(response.id());
            assertThat(payload.customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(payload.eventId()).isNotNull();
            assertThat(payload.items()).singleElement().satisfies(line -> {
                assertThat(line.productId()).isEqualTo(productId);
                assertThat(line.quantity()).isEqualTo(4);
            });
        }

        @Test
        @DisplayName("não dispara evento algum quando o pedido é recusado")
        void publishesNothingWhenRejected() {
            UUID unknown = UUID.randomUUID();
            when(inventoryClient.findByIds(anyList())).thenReturn(List.of());

            assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(List.of(new OrderItemRequest(unknown, 1))), CLIENTE))
                    .isInstanceOf(ProductUnavailableException.class);

            verify(eventPublisher, never()).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("recusa o pedido quando o catálogo não conhece o produto")
        void rejectsUnknownProduct() {
            UUID known = UUID.randomUUID();
            UUID unknown = UUID.randomUUID();
            when(inventoryClient.findByIds(anyList())).thenReturn(List.of(product(known, "10.00")));

            assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(List.of(
                    new OrderItemRequest(known, 1),
                    new OrderItemRequest(unknown, 1))), CLIENTE))
                    .isInstanceOf(ProductUnavailableException.class)
                    .satisfies(ex -> assertThat(((ProductUnavailableException) ex).getProductIds())
                            .containsExactly(unknown));

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("trata produto inativo como indisponível")
        void rejectsInactiveProduct() {
            UUID productId = UUID.randomUUID();
            when(inventoryClient.findByIds(anyList())).thenReturn(List.of(
                    new ProductSnapshot(productId, "SKU-1", "Produto", new BigDecimal("10.00"), false)));

            assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(List.of(new OrderItemRequest(productId, 1))), CLIENTE))
                    .isInstanceOf(ProductUnavailableException.class);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("propaga a indisponibilidade do catálogo sem gravar o pedido")
        void propagatesInventoryOutage() {
            UUID productId = UUID.randomUUID();
            when(inventoryClient.findByIds(anyList()))
                    .thenThrow(new InventoryUnavailableException(new RuntimeException("timeout")));

            assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(List.of(new OrderItemRequest(productId, 1))), CLIENTE))
                    .isInstanceOf(InventoryUnavailableException.class);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita o mesmo produto repetido antes de consultar o catálogo")
        void rejectsDuplicateProductBeforeCallingInventory() {
            UUID productId = UUID.randomUUID();

            assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(List.of(
                    new OrderItemRequest(productId, 1),
                    new OrderItemRequest(productId, 2))), CLIENTE))
                    .isInstanceOf(DuplicateOrderItemException.class)
                    .hasMessageContaining(productId.toString());

            verify(inventoryClient, never()).findByIds(anyList());
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    class FindById {

        @Test
        @DisplayName("lança OrderNotFoundException quando o pedido não existe")
        void throwsWhenMissing() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.findById(orderId, CLIENTE))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining(orderId.toString());
        }
    }

    @Nested
    class Cancel {

        @Test
        @DisplayName("leva um pedido pendente para CANCELLED")
        void cancelsPendingOrder() {
            Order order = pendingOrder();
            when(orderRepository.findWithItemsById(order.getId())).thenReturn(Optional.of(order));

            OrderResponse response = orderService.cancel(order.getId(), CLIENTE);

            assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("recusa cancelar um pedido que já saiu de PENDING")
        void rejectsOrderThatIsNoLongerPending() {
            Order order = pendingOrder();
            order.cancel();
            when(orderRepository.findWithItemsById(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancel(order.getId(), CLIENTE))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("CANCELLED");
        }

        @Test
        @DisplayName("lança OrderNotFoundException quando o pedido não existe")
        void throwsWhenMissing() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancel(orderId, CLIENTE))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    private static ProductSnapshot product(UUID id, String price) {
        return new ProductSnapshot(id, "SKU-" + id, "Produto", new BigDecimal(price), true);
    }

    private static Order pendingOrder() {
        return Order.create(CUSTOMER_ID,
                List.of(OrderItem.of(UUID.randomUUID(), 1, new BigDecimal("10.00"))));
    }
}
