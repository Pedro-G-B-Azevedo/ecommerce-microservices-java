package com.ecommerce.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Sem sessão e sem cookie: a autenticação é o próprio token em cada
                // requisição, então não há estado no servidor para um CSRF explorar.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/.well-known/jwks.json").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Métricas são dado operacional, não algo que qualquer cliente
                        // autenticado deva ver; só quem raspa métricas (uma conta de
                        // serviço) ou administra o sistema.
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics/**").hasAnyRole("ADMIN", "SERVICE")
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new JwtRoleConverter())))
                .build();
    }

    /**
     * Publica a chave pública para que os outros serviços validem os tokens sem
     * nunca ver a privada.
     */
    @Bean
    public RouterFunction<ServerResponse> jwksEndpoint(RSAKey rsaKey) {
        JWKSet jwkSet = new JWKSet(rsaKey.toPublicJWK());
        return RouterFunctions.route()
                .GET("/.well-known/jwks.json", request -> ServerResponse.ok().body(jwkSet.toJSONObject()))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt com custo 12: mais lento que o padrão 10, o que encarece um ataque
        // de força bruta sem pesar num login isolado.
        return new BCryptPasswordEncoder(12);
    }
}
