package com.ecommerce.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Cadastro de um novo cliente")
public record RegisterRequest(

        @Schema(example = "cliente@exemplo.com")
        @NotBlank(message = "email é obrigatório")
        @Email(message = "email inválido")
        @Size(max = 320, message = "email aceita no máximo 320 caracteres")
        String email,

        @Schema(example = "uma-senha-bem-longa")
        @NotBlank(message = "password é obrigatório")
        // O comprimento mínimo protege mais do que exigências de símbolos: senhas
        // curtas com regras decoradas são previsíveis.
        @Size(min = 12, max = 100, message = "password deve ter entre 12 e 100 caracteres")
        String password,

        @Schema(example = "Maria Silva")
        @NotBlank(message = "fullName é obrigatório")
        @Size(max = 200, message = "fullName aceita no máximo 200 caracteres")
        String fullName) {
}
