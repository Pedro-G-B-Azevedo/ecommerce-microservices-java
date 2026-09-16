package com.ecommerce.orders.repository;

import com.ecommerce.orders.entity.Order;
import com.ecommerce.orders.entity.OrderStatus;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros opcionais da listagem de pedidos.
 *
 * <p>Usar {@code Specification} em vez de derivar um método de repositório por
 * combinação de filtros evita a explosão de assinaturas
 * ({@code findByCustomerId}, {@code findByStatus}, {@code findByCustomerIdAndStatus}, ...).
 */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> hasCustomerId(UUID customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
