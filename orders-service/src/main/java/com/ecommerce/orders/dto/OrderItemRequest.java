package com.ecommerce.orders.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Item solicitado em um pedido.
 *
 * <p>O preço não faz parte do contrato: ele é lido do catálogo no momento da
 * criação. Aceitá-lo do cliente permitiria que ele definisse quanto paga.
 */
@Schema(description = "Item solicitado em um pedido")
public record OrderItemRequest(

        @Schema(example = "6f2b0d6a-2c4e-4f3a-9b21-2c6c0a0f9e11")
        @NotNull(message = "productId é obrigatório")
        UUID productId,

        @Schema(example = "2")
        @NotNull(message = "quantity é obrigatório")
        @Positive(message = "quantity deve ser maior que zero")
        Integer quantity) {
}
