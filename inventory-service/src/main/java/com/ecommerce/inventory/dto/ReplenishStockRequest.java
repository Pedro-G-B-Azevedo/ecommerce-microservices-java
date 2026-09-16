package com.ecommerce.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Entrada de estoque")
public record ReplenishStockRequest(

        @Schema(example = "25")
        @NotNull(message = "quantity é obrigatório")
        @Positive(message = "quantity deve ser maior que zero")
        Integer quantity) {
}
