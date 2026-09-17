package com.ecommerce.orders.client;

import com.ecommerce.orders.exception.InventoryUnavailableException;
import com.ecommerce.orders.service.ReservationCompensation;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Libera no inventory-service uma reserva que o pedido não vai mais usar.
 *
 * <p>Chamada síncrona de propósito: é uma compensação pontual e rara, e falhar aqui
 * deve derrubar o processamento da mensagem para que o Kafka a entregue de novo —
 * deixar estoque reservado para um pedido cancelado seria pior.
 */
@Component
public class InventoryReservationCompensation implements ReservationCompensation {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationCompensation.class);

    private final RestClient restClient;

    public InventoryReservationCompensation(RestClient inventoryRestClient) {
        this.restClient = inventoryRestClient;
    }

    @Override
    public void releaseReservation(UUID orderId) {
        try {
            restClient.post()
                    .uri("/api/v1/reservations/{orderId}/release", orderId)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Reserva do pedido {} liberada no inventory-service", orderId);
        } catch (RestClientException ex) {
            log.error("Falha ao liberar a reserva do pedido {}", orderId, ex);
            throw new InventoryUnavailableException(ex);
        }
    }
}
