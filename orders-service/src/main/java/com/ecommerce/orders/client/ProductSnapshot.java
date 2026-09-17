package com.ecommerce.orders.client;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Visão que o orders-service tem de um produto do catálogo.
 *
 * <p>Deliberadamente menor que o {@code ProductResponse} do inventory-service:
 * cada serviço define o próprio contrato de leitura e ignora o que não usa, em vez
 * de compartilhar um DTO e acoplar os dois deploys.
 */
public record ProductSnapshot(UUID id, String sku, String name, BigDecimal price, boolean active) {
}
