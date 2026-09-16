package com.ecommerce.orders.repository;

import com.ecommerce.orders.entity.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    /**
     * Carrega o pedido junto com seus itens em uma única consulta. O
     * {@code findById} padrão deixaria a coleção lazy e provocaria uma segunda ida
     * ao banco ao montar a resposta.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);
}
