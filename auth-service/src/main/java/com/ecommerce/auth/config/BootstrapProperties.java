package com.ecommerce.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Contas criadas na subida, quando configuradas.
 *
 * <p>O cadastro público só cria {@code CLIENTE}. Um administrador e a identidade dos
 * serviços precisam existir de alguma forma, e criá-los por configuração é
 * preferível a semeá-los numa migration com hash de senha fixo no repositório.
 */
@ConfigurationProperties(prefix = "auth.bootstrap")
public record BootstrapProperties(Account admin, Account serviceAccount) {

    public record Account(String email, String password, String fullName) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
