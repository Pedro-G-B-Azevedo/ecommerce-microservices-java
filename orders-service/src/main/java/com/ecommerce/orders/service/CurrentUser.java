package com.ecommerce.orders.service;

import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quem está fazendo a requisição.
 *
 * <p>Passado como parâmetro em vez de lido do {@code SecurityContextHolder} dentro
 * do serviço: assim a regra de propriedade fica explícita na assinatura e testável
 * sem montar um contexto de segurança.
 */
public record CurrentUser(UUID id, Set<String> roles) {

    private static final String ADMIN = "ADMIN";

    public static CurrentUser from(Jwt jwt) {
        java.util.List<String> claim = jwt.getClaimAsStringList("roles");
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                claim == null ? Set.of() : Set.copyOf(claim));
    }

    public boolean isAdmin() {
        return roles.contains(ADMIN);
    }

    public boolean canSee(UUID customerId) {
        return isAdmin() || id.equals(customerId);
    }
}
