package com.ecommerce.orders.config;

import com.ecommerce.orders.observability.CorrelationId;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Libera o front-end, que roda em outra origem, a chamar esta API do navegador.
 * As chamadas de serviço para serviço (orders -> inventory, orders -> auth) não
 * passam por CORS: é o navegador quem aplica essa regra, não o servidor.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", CorrelationId.HEADER));
        // Sem isso o front-end não consegue ler o header de resposta via fetch,
        // mesmo a chamada tendo sucedido.
        configuration.setExposedHeaders(List.of(CorrelationId.HEADER));
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
