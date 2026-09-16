package com.ecommerce.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Disponibilidade de um produto.
 *
 * <p>Modelado como agregado separado de {@link Product} e referenciado apenas por
 * id: são consistências distintas, e a reserva precisa travar só esta linha.
 *
 * <p>{@code availableQuantity} é o que pode ser vendido agora; {@code reservedQuantity}
 * é o que já saiu de available e aguarda a confirmação do pedido.
 */
@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected StockItem() {
        // Exigido pelo JPA.
    }

    private StockItem(UUID productId, int initialQuantity) {
        this.productId = productId;
        this.availableQuantity = initialQuantity;
        this.reservedQuantity = 0;
    }

    public static StockItem create(UUID productId, int initialQuantity) {
        if (initialQuantity < 0) {
            throw new IllegalArgumentException("A quantidade inicial não pode ser negativa");
        }
        return new StockItem(productId, initialQuantity);
    }

    public boolean hasAvailable(int quantity) {
        return availableQuantity >= quantity;
    }

    /** Move unidades de disponível para reservado. */
    public void reserve(int quantity) {
        requirePositive(quantity);
        if (!hasAvailable(quantity)) {
            throw new IllegalStateException(
                    "Estoque insuficiente: disponível " + availableQuantity + ", pedido " + quantity);
        }
        this.availableQuantity -= quantity;
        this.reservedQuantity += quantity;
    }

    /** Desfaz uma reserva: as unidades voltam a ficar disponíveis. */
    public void release(int quantity) {
        requirePositive(quantity);
        if (reservedQuantity < quantity) {
            throw new IllegalStateException(
                    "Reservado insuficiente: reservado " + reservedQuantity + ", liberação " + quantity);
        }
        this.reservedQuantity -= quantity;
        this.availableQuantity += quantity;
    }

    /** Confirma a saída: as unidades reservadas deixam o estoque de vez. */
    public void confirm(int quantity) {
        requirePositive(quantity);
        if (reservedQuantity < quantity) {
            throw new IllegalStateException(
                    "Reservado insuficiente: reservado " + reservedQuantity + ", confirmação " + quantity);
        }
        this.reservedQuantity -= quantity;
    }

    public void replenish(int quantity) {
        requirePositive(quantity);
        this.availableQuantity += quantity;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero");
        }
    }

    public UUID getProductId() {
        return productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
