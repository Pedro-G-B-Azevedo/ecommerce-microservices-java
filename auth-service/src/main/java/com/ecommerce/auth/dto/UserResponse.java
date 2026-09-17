package com.ecommerce.auth.dto;

import com.ecommerce.auth.entity.Role;
import com.ecommerce.auth.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Dados públicos de um usuário. O hash da senha nunca sai daqui. */
@Schema(description = "Usuário")
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        Set<Role> roles,
        boolean enabled,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRoles(), user.isEnabled(), user.getCreatedAt());
    }
}
