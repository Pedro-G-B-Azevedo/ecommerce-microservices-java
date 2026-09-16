package com.ecommerce.inventory.dto;

import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.entity.StockReservation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Reserva de estoque de um pedido")
public record ReservationResponse(
        UUID orderId,
        ReservationStatus status,
        List<ReservationItemResponse> items,
        Instant createdAt) {

    public static ReservationResponse from(List<StockReservation> reservations) {
        StockReservation first = reservations.get(0);
        return new ReservationResponse(
                first.getOrderId(),
                first.getStatus(),
                reservations.stream().map(ReservationItemResponse::from).toList(),
                first.getCreatedAt());
    }

    @Schema(description = "Item reservado")
    public record ReservationItemResponse(UUID productId, int quantity) {

        static ReservationItemResponse from(StockReservation reservation) {
            return new ReservationItemResponse(reservation.getProductId(), reservation.getQuantity());
        }
    }
}
