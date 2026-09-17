package com.ecommerce.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Métricas são dado operacional, não algo que qualquer cliente
                        // autenticado deva ver; só quem raspa métricas (uma conta de
                        // serviço) ou administra o sistema.
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics/**").hasAnyRole("ADMIN", "SERVICE")
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // O catálogo é público: numa loja, navegar pelos produtos e ver
                        // preços não exige conta. Estoque é leitura de catálogo também.
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        // Alterar o catálogo, não.
                        .requestMatchers("/api/v1/products/**").hasRole("ADMIN")
                        // Reservas são operação interna da saga, nunca de um cliente.
                        .requestMatchers("/api/v1/reservations/**").hasAnyRole("SERVICE", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new JwtRoleConverter())))
                .build();
    }
}
