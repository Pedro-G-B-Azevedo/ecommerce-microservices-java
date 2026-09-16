package com.ecommerce.inventory.exception;

import java.util.UUID;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(UUID orderId) {
        super("Não há reserva para o pedido: " + orderId);
    }
}
