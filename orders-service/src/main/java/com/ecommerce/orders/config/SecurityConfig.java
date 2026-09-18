package com.ecommerce.orders.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Sem sessão e sem cookie: a autenticação é o token em cada requisição,
                // então não há estado no servidor para um CSRF explorar.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Métricas são dado operacional, não algo que qualquer cliente
                        // autenticado deva ver; só quem raspa métricas (uma conta de
                        // serviço) ou administra o sistema.
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics/**").hasAnyRole("ADMIN", "SERVICE")
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Criar pedido é coisa de cliente; um administrador não compra
                        // em nome de ninguém.
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders").hasRole("CLIENTE")
                        .requestMatchers("/api/v1/orders/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new JwtRoleConverter())))
                .build();
    }
}
