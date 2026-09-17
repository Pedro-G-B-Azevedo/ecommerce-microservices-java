package com.ecommerce.orders.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Endereço e tempos-limite do inventory-service.
 *
 * <p>Os timeouts são curtos de propósito: a criação de pedido é síncrona, e uma
 * chamada pendurada seguraria a thread e o cliente junto.
 */
@ConfigurationProperties(prefix = "inventory")
public record InventoryProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout) {

    public InventoryProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(3);
        }
    }
}
