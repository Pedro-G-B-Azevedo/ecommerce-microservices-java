package com.ecommerce.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Registro de uma reserva de estoque feita para um pedido.
 *
 * <p>A unique key {@code (order_id, product_id)} é o que torna a reserva idempotente:
 * quando a etapa 4 reentregar o mesmo evento de pedido, a segunda tentativa não
 * consegue criar uma reserva duplicada.
 */
@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected StockReservation() {
        // Exigido pelo JPA.
    }

    private StockReservation(UUID orderId, UUID productId, int quantity) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = ReservationStatus.RESERVED;
    }

    public static StockReservation create(UUID orderId, UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero");
        }
        return new StockReservation(orderId, productId, quantity);
    }

    public void confirm() {
        requireReserved("confirmada");
        this.status = ReservationStatus.CONFIRMED;
    }

    public void release() {
        requireReserved("liberada");
        this.status = ReservationStatus.RELEASED;
    }

    public boolean isReserved() {
        return status == ReservationStatus.RESERVED;
    }

    private void requireReserved(String action) {
        if (!isReserved()) {
            throw new IllegalStateException(
                    "Apenas uma reserva ativa pode ser " + action + "; status atual: " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
