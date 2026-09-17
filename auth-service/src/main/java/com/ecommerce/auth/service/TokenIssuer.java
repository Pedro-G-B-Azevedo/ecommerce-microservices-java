package com.ecommerce.auth.service;

import com.ecommerce.auth.config.JwtProperties;
import com.ecommerce.auth.dto.TokenResponse;
import com.ecommerce.auth.entity.Role;
import com.ecommerce.auth.entity.User;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Monta e assina o token de acesso. */
@Component
public class TokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public TokenIssuer(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public TokenResponse issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        Set<String> roles = user.getRoles().stream().map(Role::name).collect(Collectors.toSet());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                // O subject é o id do usuário, não o e-mail: é o que os outros
                // serviços usam como customerId, e não muda se o e-mail mudar.
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("roles", roles)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return TokenResponse.bearer(token, properties.accessTokenTtl().toSeconds(), expiresAt, roles);
    }
}
