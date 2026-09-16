package com.ecommerce.orders.dto;

import com.ecommerce.orders.entity.Order;
import com.ecommerce.orders.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Versão reduzida usada na listagem. Os itens ficam de fora de propósito: carregá-los
 * para cada pedido da página provocaria N+1 sem que a listagem precise deles.
 */
@Schema(description = "Resumo de um pedido, usado nas listagens")
public record OrderSummaryResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt());
    }
}
