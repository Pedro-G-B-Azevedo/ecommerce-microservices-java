package com.ecommerce.orders.service;

import com.ecommerce.orders.dto.CreateOrderRequest;
import com.ecommerce.orders.dto.OrderItemRequest;
import com.ecommerce.orders.dto.OrderResponse;
import com.ecommerce.orders.dto.OrderSummaryResponse;
import com.ecommerce.orders.dto.PageResponse;
import com.ecommerce.orders.entity.Order;
import com.ecommerce.orders.entity.OrderItem;
import com.ecommerce.orders.entity.OrderStatus;
import com.ecommerce.orders.exception.DuplicateOrderItemException;
import com.ecommerce.orders.exception.InvalidOrderStateException;
import com.ecommerce.orders.exception.OrderNotFoundException;
import com.ecommerce.orders.repository.OrderRepository;
import com.ecommerce.orders.repository.OrderSpecifications;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Cria um pedido pendente.
     *
     * <p>A partir da etapa 4 esta operação também publicará o evento
     * {@code OrderCreated}, que dispara a reserva de estoque.
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        rejectDuplicateProducts(request.items());

        List<OrderItem> items = request.items().stream()
                .map(OrderItemRequest::toEntity)
                .toList();

        Order order = orderRepository.save(Order.create(request.customerId(), items));
        log.info("Pedido {} criado para o cliente {} no valor de {}",
                order.getId(), order.getCustomerId(), order.getTotalAmount());

        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(UUID orderId) {
        return orderRepository.findWithItemsById(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> search(UUID customerId, OrderStatus status, Pageable pageable) {
        List<Specification<Order>> filters = new ArrayList<>();
        if (customerId != null) {
            filters.add(OrderSpecifications.hasCustomerId(customerId));
        }
        if (status != null) {
            filters.add(OrderSpecifications.hasStatus(status));
        }

        Page<Order> page = orderRepository.findAll(Specification.allOf(filters), pageable);
        return PageResponse.from(page, OrderSummaryResponse::from);
    }

    @Transactional
    public OrderResponse cancel(UUID orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        try {
            order.cancel();
        } catch (IllegalStateException ex) {
            // A entidade protege a própria consistência; aqui a violação vira um
            // erro de aplicação, que o handler traduz para 409.
            throw new InvalidOrderStateException(ex.getMessage());
        }

        log.info("Pedido {} cancelado", orderId);
        return OrderResponse.from(order);
    }

    private void rejectDuplicateProducts(List<OrderItemRequest> items) {
        Set<UUID> seen = new HashSet<>();
        for (OrderItemRequest item : items) {
            if (!seen.add(item.productId())) {
                throw new DuplicateOrderItemException(item.productId());
            }
        }
    }
}
