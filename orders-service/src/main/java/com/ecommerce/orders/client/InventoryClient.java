package com.ecommerce.orders.client;

import com.ecommerce.orders.exception.InventoryUnavailableException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Acesso ao catálogo do inventory-service. */
@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private static final ParameterizedTypeReference<List<ProductSnapshot>> PRODUCT_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public InventoryClient(RestClient inventoryRestClient) {
        this.restClient = inventoryRestClient;
    }

    /**
     * Consulta vários produtos de uma vez.
     *
     * <p>Uma chamada por item transformaria um pedido de dez linhas em dez idas à
     * rede, cada uma com sua chance de falhar.
     *
     * <p>Ids inexistentes simplesmente não vêm na resposta; quem chama compara o que
     * pediu com o que voltou.
     */
    public List<ProductSnapshot> findByIds(List<UUID> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        try {
            List<ProductSnapshot> products = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/products/by-ids")
                            .queryParam("ids", productIds)
                            .build())
                    .retrieve()
                    .body(PRODUCT_LIST);

            return products == null ? List.of() : products;
        } catch (RestClientException ex) {
            // Inclui timeout, recusa de conexão e respostas de erro do inventory.
            log.warn("Falha ao consultar o catálogo no inventory-service: {}", ex.getMessage());
            throw new InventoryUnavailableException(ex);
        }
    }
}
