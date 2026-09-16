package com.ecommerce.inventory.dto;

import com.ecommerce.inventory.entity.StockItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Situação do estoque de um produto")
public record StockResponse(
        UUID productId,
        @Schema(description = "Unidades que podem ser vendidas agora")
        int availableQuantity,
        @Schema(description = "Unidades separadas para pedidos ainda não confirmados")
        int reservedQuantity,
        Instant updatedAt) {

    public static StockResponse from(StockItem stock) {
        return new StockResponse(
                stock.getProductId(),
                stock.getAvailableQuantity(),
                stock.getReservedQuantity(),
                stock.getUpdatedAt());
    }
}
