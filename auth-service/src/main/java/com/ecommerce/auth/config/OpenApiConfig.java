package com.ecommerce.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Nome referenciado pelas anotações {@code @SecurityRequirement} dos controllers. */
    public static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de autenticação")
                        .version("0.1.0")
                        .description("""
                                Cadastro de clientes, emissão de tokens JWT e publicação da chave pública usada para validá-los.

                                As rotas protegidas exigem um token JWT emitido pelo auth-service em
                                `POST /api/v1/auth/login`. Clique em **Authorize** e informe apenas o
                                token: o prefixo `Bearer` é acrescentado automaticamente.
                                """)
                        .contact(new Contact().name("Pedro G. B. Azevedo")
                                .url("https://github.com/Pedro-G-B-Azevedo"))
                        .license(new License().name("MIT")
                                .url("https://github.com/Pedro-G-B-Azevedo/ecommerce-microservices-java/blob/main/LICENSE")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Desenvolvimento local")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token emitido pelo auth-service")));
    }
}
