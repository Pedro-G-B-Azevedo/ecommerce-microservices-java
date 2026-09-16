package com.ecommerce.orders.dto;

import com.ecommerce.orders.entity.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Item solicitado em um pedido")
public record OrderItemRequest(

        @Schema(example = "6f2b0d6a-2c4e-4f3a-9b21-2c6c0a0f9e11")
        @NotNull(message = "productId é obrigatório")
        UUID productId,

        @Schema(example = "2")
        @NotNull(message = "quantity é obrigatório")
        @Positive(message = "quantity deve ser maior que zero")
        Integer quantity,

        // Provisório: a partir da etapa 3 o preço passa a ser consultado no
        // inventory-service e este campo sai do contrato — preço não é algo que
        // o cliente deva informar.
        @Schema(example = "149.90")
        @NotNull(message = "unitPrice é obrigatório")
        @DecimalMin(value = "0.00", message = "unitPrice não pode ser negativo")
        @Digits(integer = 17, fraction = 2, message = "unitPrice aceita no máximo duas casas decimais")
        BigDecimal unitPrice) {

    public OrderItem toEntity() {
        return OrderItem.of(productId, quantity, unitPrice);
    }
}
