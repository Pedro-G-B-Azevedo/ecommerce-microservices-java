package com.ecommerce.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
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
                        .requestMatchers("/api/v1/notifications/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new JwtRoleConverter())))
                .build();
    }
}
