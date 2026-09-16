package com.ecommerce.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

@Schema(description = "Produto e quantidade a reservar")
public record ReservationItemRequest(

        @NotNull(message = "productId é obrigatório")
        UUID productId,

        @NotNull(message = "quantity é obrigatório")
        @Positive(message = "quantity deve ser maior que zero")
        Integer quantity) {
}
