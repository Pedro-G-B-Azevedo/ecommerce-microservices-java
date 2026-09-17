package com.ecommerce.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A quem notificar sobre um pedido.
 *
 * <p>Modelo de leitura montado a partir de {@code OrderCreated}. Os eventos de
 * estoque trazem apenas o pedido — o inventory-service não conhece o cliente — então
 * é aqui que o destinatário do desfecho é recuperado.
 */
@Entity
@Table(name = "order_contacts")
public class OrderContact {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderContact() {
        // Exigido pelo JPA.
    }

    public OrderContact(UUID orderId, UUID customerId) {
        this.orderId = orderId;
        this.customerId = customerId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
