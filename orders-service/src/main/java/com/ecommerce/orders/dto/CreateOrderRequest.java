package com.ecommerce.orders.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Dados para criação de um pedido.
 *
 * <p>O cliente não faz parte do corpo: é o subject do token. Aceitá-lo aqui
 * permitiria criar pedidos em nome de outra pessoa.
 */
@Schema(description = "Dados para criação de um pedido")
public record CreateOrderRequest(

        @Valid
        @NotEmpty(message = "O pedido precisa de ao menos um item")
        @Size(max = 100, message = "Um pedido aceita no máximo 100 itens")
        List<OrderItemRequest> items) {
}
