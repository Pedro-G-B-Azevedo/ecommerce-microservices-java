package com.ecommerce.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do token emitido.
 *
 * <p>As chaves vêm em PEM por variável de ambiente. Quando não vêm, o serviço gera
 * um par efêmero ao subir — conveniente em desenvolvimento, inviável em produção,
 * onde reiniciar invalidaria todos os tokens em circulação.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String issuer,
        Duration accessTokenTtl,
        String privateKey,
        String publicKey) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "https://auth.ecommerce.local";
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofHours(1);
        }
    }

    public boolean hasConfiguredKeys() {
        return privateKey != null && !privateKey.isBlank()
                && publicKey != null && !publicKey.isBlank();
    }
}
