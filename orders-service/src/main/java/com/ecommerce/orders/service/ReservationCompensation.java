package com.ecommerce.orders.service;

import java.util.UUID;

/**
 * Desfaz uma reserva de estoque que deixou de ser necessária.
 *
 * <p>Existe como interface para que a saga dependa da intenção, e não do transporte:
 * hoje é uma chamada REST ao inventory-service.
 */
public interface ReservationCompensation {

    void releaseReservation(UUID orderId);
}
