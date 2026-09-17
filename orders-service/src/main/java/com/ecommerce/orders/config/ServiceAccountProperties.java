package com.ecommerce.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Credenciais com que o orders-service se identifica perante os demais serviços. */
@ConfigurationProperties(prefix = "service-account")
public record ServiceAccountProperties(String authBaseUrl, String email, String password) {
}
