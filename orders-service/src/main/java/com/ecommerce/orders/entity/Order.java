package com.ecommerce.orders.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Raiz do agregado de pedido.
 *
 * <p>A entidade não expõe setters: o total é sempre derivado dos itens e as
 * mudanças de status passam por métodos que validam a transição, de modo que um
 * pedido inválido não consegue existir em memória.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Version
    private Long version;

    protected Order() {
        // Exigido pelo JPA.
    }

    private Order(UUID customerId, Collection<OrderItem> items) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.status = OrderStatus.PENDING;
        items.forEach(this::addItem);
        this.totalAmount = calculateTotal();
    }

    /**
     * Cria um pedido pendente. O total nunca vem do cliente: é sempre somado a
     * partir dos itens.
     */
    public static Order create(UUID customerId, Collection<OrderItem> items) {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Um pedido precisa de ao menos um item");
        }
        return new Order(customerId, items);
    }

    /**
     * Confirma o pedido após o inventory-service separar o estoque.
     *
     * <p>Confirmar um pedido já confirmado é silenciosamente aceito: o evento pode
     * chegar duas vezes, e a segunda não deve virar erro.
     */
    public void confirm() {
        if (status == OrderStatus.CONFIRMED) {
            return;
        }
        requirePending("confirmado");
        this.status = OrderStatus.CONFIRMED;
    }

    /** Rejeita o pedido porque o estoque não pôde ser separado. */
    public void reject(String reason) {
        if (status == OrderStatus.REJECTED) {
            return;
        }
        requirePending("rejeitado");
        this.status = OrderStatus.REJECTED;
        this.rejectionReason = reason;
    }

    private void requirePending(String action) {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Apenas um pedido pendente pode ser " + action + "; status atual: " + status);
        }
    }

    /** Cancelamento a pedido do cliente, possível apenas enquanto o pedido está pendente. */
    public void cancel() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Apenas pedidos pendentes podem ser cancelados; status atual: " + status);
        }
        this.status = OrderStatus.CANCELLED;
    }

    private void addItem(OrderItem item) {
        item.attachTo(this);
        this.items.add(item);
    }

    private BigDecimal calculateTotal() {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Long getVersion() {
        return version;
    }
}
