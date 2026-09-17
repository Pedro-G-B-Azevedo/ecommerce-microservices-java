package com.ecommerce.orders.client;

import com.ecommerce.orders.config.ServiceAccountProperties;
import com.ecommerce.orders.exception.InventoryUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Token que identifica o próprio orders-service perante o inventory-service.
 *
 * <p>Existe porque a compensação da saga — liberar uma reserva quando o pedido foi
 * cancelado — nasce de um evento do Kafka, onde não há usuário autenticado para
 * repassar. Propagar o token do cliente também seria errado: quem pede a liberação
 * é o serviço, não a pessoa.
 *
 * <p>O token é guardado em memória e renovado pouco antes de expirar, para não
 * autenticar a cada chamada.
 */
@Component
public class ServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenProvider.class);
    private static final Duration RENEW_BEFORE_EXPIRY = Duration.ofMinutes(1);

    private final RestClient authRestClient;
    private final ServiceAccountProperties properties;
    private final AtomicReference<CachedToken> cached = new AtomicReference<>();

    public ServiceTokenProvider(RestClient authRestClient, ServiceAccountProperties properties) {
        this.authRestClient = authRestClient;
        this.properties = properties;
    }

    public String currentToken() {
        CachedToken token = cached.get();
        if (token != null && token.isUsable()) {
            return token.value();
        }
        CachedToken fresh = login();
        cached.set(fresh);
        return fresh.value();
    }

    private CachedToken login() {
        try {
            Map<String, Object> response = authRestClient.post()
                    .uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", properties.email(), "password", properties.password()))
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("accessToken") == null) {
                throw new IllegalStateException("Resposta de login sem token");
            }

            long expiresIn = ((Number) response.getOrDefault("expiresIn", 3600)).longValue();
            log.info("Token de serviço renovado; expira em {}s", expiresIn);
            return new CachedToken(
                    (String) response.get("accessToken"),
                    Instant.now().plusSeconds(expiresIn));

        } catch (RestClientException | IllegalStateException ex) {
            log.error("Falha ao obter o token de serviço no auth-service", ex);
            throw new InventoryUnavailableException(ex);
        }
    }

    private record CachedToken(String value, Instant expiresAt) {

        boolean isUsable() {
            return Instant.now().isBefore(expiresAt.minus(RENEW_BEFORE_EXPIRY));
        }
    }
}
