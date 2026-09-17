package com.ecommerce.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecommerce.orders.entity.Order;
import com.ecommerce.orders.entity.OrderItem;
import com.ecommerce.orders.entity.OrderStatus;
import com.ecommerce.orders.entity.ProcessedEvent;
import com.ecommerce.orders.exception.OrderNotFoundException;
import com.ecommerce.orders.repository.OrderRepository;
import com.ecommerce.orders.repository.ProcessedEventRepository;
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
class OrderSagaServiceTest {

    private static final UUID EVENT_ID = UUID.randomUUID();

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private ReservationCompensation compensation;

    @InjectMocks
    private OrderSagaService sagaService;

    @Nested
    class Confirm {

        @Test
        @DisplayName("leva o pedido pendente para CONFIRMED")
        void confirmsPendingOrder() {
            Order order = pendingOrder();
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(false);
            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            sagaService.confirm(EVENT_ID, order.getId());

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(processedEventRepository).save(any(ProcessedEvent.class));
        }

        @Test
        @DisplayName("ignora a repetição de um evento já processado")
        void ignoresAlreadyProcessedEvent() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            sagaService.confirm(EVENT_ID, UUID.randomUUID());

            verifyNoInteractions(orderRepository);
            verify(processedEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("pedido cancelado durante a reserva: libera o estoque em vez de confirmar")
        void releasesReservationWhenOrderWasCancelled() {
            Order order = pendingOrder();
            order.cancel();
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(false);
            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            sagaService.confirm(EVENT_ID, order.getId());

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(compensation).releaseReservation(order.getId());
        }

        @Test
        @DisplayName("confirmar duas vezes o mesmo pedido não é erro")
        void confirmingTwiceIsHarmless() {
            Order order = pendingOrder();
            order.confirm();
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(false);
            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            sagaService.confirm(EVENT_ID, order.getId());

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verifyNoInteractions(compensation);
        }

        @Test
        @DisplayName("evento para pedido inexistente falha, para que o Kafka reentregue")
        void throwsForUnknownOrder() {
            UUID orderId = UUID.randomUUID();
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(false);
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sagaService.confirm(EVENT_ID, orderId))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    class Reject {

        @Test
        @DisplayName("leva o pedido para REJECTED guardando o motivo")
        void rejectsPendingOrder() {
            Order order = pendingOrder();
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(false);
            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            sagaService.reject(EVENT_ID, order.getId(), "Estoque insuficiente para 1 item(ns)");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(order.getRejectionReason()).isEqualTo("Estoque insuficiente para 1 item(ns)");
        }

        @Test
        @DisplayName("pedido já cancelado não vira rejeitado e nada é compensado")
        void leavesCancelledOrderAlone() {
            Order order = pendingOrder();
            order.cancel();
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(false);
            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            sagaService.reject(EVENT_ID, order.getId(), "Estoque insuficiente");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verifyNoInteractions(compensation);
        }

        @Test
        @DisplayName("ignora a repetição de um evento já processado")
        void ignoresAlreadyProcessedEvent() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            sagaService.reject(EVENT_ID, UUID.randomUUID(), "qualquer motivo");

            verifyNoInteractions(orderRepository);
        }
    }

    private static Order pendingOrder() {
        return Order.create(UUID.randomUUID(),
                List.of(OrderItem.of(UUID.randomUUID(), 1, new BigDecimal("10.00"))));
    }
}
