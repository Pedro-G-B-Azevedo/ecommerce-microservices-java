package com.ecommerce.orders.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dados para criação de um pedido")
public record CreateOrderRequest(

        // Provisório: a partir da etapa 6 o cliente é extraído do JWT em vez de
        // ser informado no corpo da requisição.
        @Schema(example = "3f1c9a7e-7b5d-4a2f-8c10-5d9e1b3a4c22")
        @NotNull(message = "customerId é obrigatório")
        UUID customerId,

        @Valid
        @NotEmpty(message = "O pedido precisa de ao menos um item")
        @Size(max = 100, message = "Um pedido aceita no máximo 100 itens")
        List<OrderItemRequest> items) {
}
