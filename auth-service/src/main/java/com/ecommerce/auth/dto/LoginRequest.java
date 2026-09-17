package com.ecommerce.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciais de acesso")
public record LoginRequest(

        @Schema(example = "cliente@exemplo.com")
        @NotBlank(message = "email é obrigatório")
        String email,

        @NotBlank(message = "password é obrigatório")
        String password) {
}
