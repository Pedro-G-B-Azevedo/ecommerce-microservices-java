package com.ecommerce.orders.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.orders.entity.Order;
import com.ecommerce.orders.entity.OrderItem;
import com.ecommerce.orders.entity.OrderStatus;
import com.ecommerce.orders.support.PostgresContainerSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

/**
 * Exercita o mapeamento e as constraints contra o PostgreSQL real criado pelas
 * migrations Flyway — é o que garante que entidade e schema não divergem.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("persiste o pedido com seus itens em cascata")
    void persistsOrderWithItems() {
        Order saved = orderRepository.saveAndFlush(orderWith(
                OrderItem.of(UUID.randomUUID(), 2, new BigDecimal("149.90")),
                OrderItem.of(UUID.randomUUID(), 1, new BigDecimal("59.00"))));

        entityManager.clear();

        Order reloaded = orderRepository.findWithItemsById(saved.getId()).orElseThrow();
        assertThat(reloaded.getItems()).hasSize(2);
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo("358.80");
    }

    @Test
    @DisplayName("preenche os timestamps e a versão do bloqueio otimista na primeira gravação")
    void populatesAuditFields() {
        Order saved = orderRepository.saveAndFlush(
                orderWith(OrderItem.of(UUID.randomUUID(), 1, new BigDecimal("10.00"))));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    @DisplayName("a unique key impede o mesmo produto duas vezes no mesmo pedido")
    void rejectsDuplicateProductInSameOrder() {
        UUID productId = UUID.randomUUID();
        Order order = orderWith(
                OrderItem.of(productId, 1, new BigDecimal("10.00")),
                OrderItem.of(productId, 2, new BigDecimal("10.00")));

        assertThatThrownBy(() -> orderRepository.saveAndFlush(order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("filtra por cliente e por status usando as specifications")
    void filtersByCustomerAndStatus() {
        UUID customerId = UUID.randomUUID();
        orderRepository.save(Order.create(customerId,
                List.of(OrderItem.of(UUID.randomUUID(), 1, new BigDecimal("10.00")))));

        Order cancelled = Order.create(customerId,
                List.of(OrderItem.of(UUID.randomUUID(), 1, new BigDecimal("20.00"))));
        cancelled.cancel();
        orderRepository.save(cancelled);

        // Pedido de outro cliente, que não deve aparecer em nenhum dos filtros.
        orderRepository.save(Order.create(UUID.randomUUID(),
                List.of(OrderItem.of(UUID.randomUUID(), 1, new BigDecimal("30.00")))));
        orderRepository.flush();

        Specification<Order> byCustomer = OrderSpecifications.hasCustomerId(customerId);
        assertThat(orderRepository.findAll(byCustomer, PageRequest.of(0, 10))).hasSize(2);

        Specification<Order> byCustomerPending = Specification.allOf(
                byCustomer, OrderSpecifications.hasStatus(OrderStatus.PENDING));
        assertThat(orderRepository.findAll(byCustomerPending, PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING));
    }

    private static Order orderWith(OrderItem... items) {
        return Order.create(UUID.randomUUID(), List.of(items));
    }
}
