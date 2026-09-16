package com.ecommerce.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.orders.dto.CreateOrderRequest;
import com.ecommerce.orders.dto.OrderItemRequest;
import com.ecommerce.orders.dto.OrderResponse;
import com.ecommerce.orders.entity.Order;
import com.ecommerce.orders.entity.OrderItem;
import com.ecommerce.orders.entity.OrderStatus;
import com.ecommerce.orders.exception.DuplicateOrderItemException;
import com.ecommerce.orders.exception.InvalidOrderStateException;
import com.ecommerce.orders.exception.OrderNotFoundException;
import com.ecommerce.orders.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Nested
    class Create {

        @Test
        @DisplayName("soma os subtotais dos itens no total do pedido")
        void calculatesTotalFromItems() {
            when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

            CreateOrderRequest request = new CreateOrderRequest(CUSTOMER_ID, List.of(
                    new OrderItemRequest(UUID.randomUUID(), 2, new BigDecimal("149.90")),
                    new OrderItemRequest(UUID.randomUUID(), 1, new BigDecimal("59.00"))));

            OrderResponse response = orderService.create(request);

            // 2 x 149.90 + 1 x 59.00
            assertThat(response.totalAmount()).isEqualByComparingTo("358.80");
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.items()).hasSize(2);
        }

        @Test
        @DisplayName("expõe o subtotal de cada item")
        void exposesItemSubtotal() {
            when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
            UUID productId = UUID.randomUUID();

            OrderResponse response = orderService.create(new CreateOrderRequest(
                    CUSTOMER_ID, List.of(new OrderItemRequest(productId, 3, new BigDecimal("10.00")))));

            assertThat(response.items()).singleElement().satisfies(item -> {
                assertThat(item.productId()).isEqualTo(productId);
                assertThat(item.subtotal()).isEqualByComparingTo("30.00");
            });
        }

        @Test
        @DisplayName("rejeita o mesmo produto repetido, sem chegar ao banco")
        void rejectsDuplicateProduct() {
            UUID productId = UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(CUSTOMER_ID, List.of(
                    new OrderItemRequest(productId, 1, new BigDecimal("10.00")),
                    new OrderItemRequest(productId, 2, new BigDecimal("10.00"))));

            assertThatThrownBy(() -> orderService.create(request))
                    .isInstanceOf(DuplicateOrderItemException.class)
                    .hasMessageContaining(productId.toString());

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

            assertThatThrownBy(() -> orderService.findById(orderId))
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

            OrderResponse response = orderService.cancel(order.getId());

            assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("recusa cancelar um pedido que já saiu de PENDING")
        void rejectsOrderThatIsNoLongerPending() {
            Order order = pendingOrder();
            order.cancel();
            when(orderRepository.findWithItemsById(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancel(order.getId()))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("CANCELLED");
        }

        @Test
        @DisplayName("lança OrderNotFoundException quando o pedido não existe")
        void throwsWhenMissing() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancel(orderId))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    private static Order pendingOrder() {
        return Order.create(CUSTOMER_ID,
                List.of(OrderItem.of(UUID.randomUUID(), 1, new BigDecimal("10.00"))));
    }
}
