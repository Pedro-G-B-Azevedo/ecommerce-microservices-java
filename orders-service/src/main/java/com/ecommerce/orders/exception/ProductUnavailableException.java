package com.ecommerce.orders.exception;

import java.util.List;
import java.util.UUID;

/**
 * Lançada quando o pedido referencia produtos que não existem ou estão inativos.
 *
 * <p>Carrega todos os ids problemáticos de uma vez, para que o cliente não precise
 * descobrir um por vez a cada tentativa.
 */
public class ProductUnavailableException extends RuntimeException {

    private final List<UUID> productIds;

    public ProductUnavailableException(List<UUID> productIds) {
        super("Produtos indisponíveis no catálogo: " + productIds.size());
        this.productIds = List.copyOf(productIds);
    }

    public List<UUID> getProductIds() {
        return productIds;
    }
}
