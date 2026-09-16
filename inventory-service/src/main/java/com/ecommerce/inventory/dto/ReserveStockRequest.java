package com.ecommerce.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Schema(description = "Pedido de reserva de estoque para um pedido")
public record ReserveStockRequest(

        @Schema(description = "Pedido dono da reserva; também é a chave de idempotência")
        @NotNull(message = "orderId é obrigatório")
        UUID orderId,

        @Valid
        @NotEmpty(message = "A reserva precisa de ao menos um item")
        List<ReservationItemRequest> items) {
}
