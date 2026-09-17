package com.ecommerce.orders.service;

import com.ecommerce.contracts.OrderCreatedEvent;
import com.ecommerce.orders.client.InventoryClient;
import com.ecommerce.orders.client.ProductSnapshot;
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
import com.ecommerce.orders.exception.ProductUnavailableException;
import com.ecommerce.orders.exception.OrderAccessDeniedException;
import com.ecommerce.orders.exception.OrderNotFoundException;
import com.ecommerce.orders.repository.OrderRepository;
import com.ecommerce.orders.messaging.OrderCreatedDomainEvent;
import com.ecommerce.orders.repository.OrderSpecifications;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository,
                        InventoryClient inventoryClient,
                        ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Cria um pedido pendente.
     *
     * <p>A partir da etapa 4 esta operação também publicará o evento
     * {@code OrderCreated}, que dispara a reserva de estoque.
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest request, CurrentUser currentUser) {
        rejectDuplicateProducts(request.items());

        // O preço vem do catálogo, nunca do cliente. Uma única chamada em lote
        // resolve o pedido inteiro.
        Map<UUID, ProductSnapshot> catalog = loadCatalog(request.items());

        List<OrderItem> items = request.items().stream()
                .map(item -> OrderItem.of(
                        item.productId(), item.quantity(), catalog.get(item.productId()).price()))
                .toList();

        Order order = orderRepository.save(Order.create(currentUser.id(), items));
        log.info("Pedido {} criado para o cliente {} no valor de {}",
                order.getId(), order.getCustomerId(), order.getTotalAmount());

        // Publicado no Kafka apenas depois do commit desta transação; ver
        // OrderEventPublisher.
        eventPublisher.publishEvent(new OrderCreatedDomainEvent(toEvent(order)));

        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(UUID orderId, CurrentUser currentUser) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        requireVisible(order, currentUser);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> search(UUID customerId, OrderStatus status,
                                                     Pageable pageable, CurrentUser currentUser) {
        List<Specification<Order>> filters = new ArrayList<>();

        // Um cliente só enxerga os próprios pedidos: o filtro é imposto, não
        // aceito da requisição. Sem isso, bastaria informar outro customerId.
        UUID effectiveCustomerId = currentUser.isAdmin() ? customerId : currentUser.id();
        if (effectiveCustomerId != null) {
            filters.add(OrderSpecifications.hasCustomerId(effectiveCustomerId));
        }
        if (status != null) {
            filters.add(OrderSpecifications.hasStatus(status));
        }

        Page<Order> page = orderRepository.findAll(Specification.allOf(filters), pageable);
        return PageResponse.from(page, OrderSummaryResponse::from);
    }

    @Transactional
    public OrderResponse cancel(UUID orderId, CurrentUser currentUser) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        requireVisible(order, currentUser);

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

    private static void requireVisible(Order order, CurrentUser currentUser) {
        if (!currentUser.canSee(order.getCustomerId())) {
            throw new OrderAccessDeniedException();
        }
    }

    private static OrderCreatedEvent toEvent(Order order) {
        return new OrderCreatedEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getCustomerId(),
                order.getItems().stream()
                        .map(item -> new OrderCreatedEvent.OrderLine(item.getProductId(), item.getQuantity()))
                        .toList(),
                order.getTotalAmount(),
                Instant.now());
    }

    /**
     * Busca no catálogo os produtos do pedido e recusa o que não estiver disponível.
     *
     * <p>Produto inexistente e produto inativo são tratados do mesmo jeito: nenhum dos
     * dois pode ser vendido, e distinguir os casos para o cliente só revelaria o que
     * existe no catálogo sem estar à venda.
     */
    private Map<UUID, ProductSnapshot> loadCatalog(List<OrderItemRequest> items) {
        List<UUID> productIds = items.stream().map(OrderItemRequest::productId).toList();

        Map<UUID, ProductSnapshot> catalog = new LinkedHashMap<>();
        for (ProductSnapshot product : inventoryClient.findByIds(productIds)) {
            if (product.active()) {
                catalog.put(product.id(), product);
            }
        }

        List<UUID> unavailable = productIds.stream()
                .filter(id -> !catalog.containsKey(id))
                .toList();
        if (!unavailable.isEmpty()) {
            throw new ProductUnavailableException(unavailable);
        }
        return catalog;
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
