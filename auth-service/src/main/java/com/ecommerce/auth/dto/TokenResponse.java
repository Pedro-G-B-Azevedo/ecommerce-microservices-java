package com.ecommerce.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;

@Schema(description = "Token de acesso emitido")
public record TokenResponse(
        String accessToken,
        String tokenType,
        @Schema(description = "Segundos até a expiração")
        long expiresIn,
        Instant expiresAt,
        Set<String> roles) {

    public static TokenResponse bearer(String token, long expiresIn, Instant expiresAt, Set<String> roles) {
        return new TokenResponse(token, "Bearer", expiresIn, expiresAt, roles);
    }
}
