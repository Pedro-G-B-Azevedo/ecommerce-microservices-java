package com.ecommerce.orders.dto;

import com.ecommerce.orders.entity.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Item de um pedido")
public record OrderItemResponse(
        UUID productId,
        int quantity,
        BigDecimal unitPrice,
        @Schema(description = "Preço unitário multiplicado pela quantidade")
        BigDecimal subtotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(), item.getQuantity(), item.getUnitPrice(), item.subtotal());
    }
}
