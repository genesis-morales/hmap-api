package com.hmap.backend.user.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hmap.backend.auth.entity.Auth;

/**
 * Usuario tal como lo consume el panel de administrador (HU-034). A diferencia
 * de {@link com.hmap.backend.auth.dto.UserDTO} (perfil propio en GET /auth/me),
 * expone {@code active}, {@code deactivation_reason} y {@code created_at} para
 * la gestión de cuentas internas.
 */
public record AdminUserDTO(
        Long id,
        String name,
        @JsonProperty("last_name") String lastName,
        String email,
        String phone,
        String role,
        boolean active,
        @JsonProperty("deactivation_reason") String deactivationReason,
        @JsonProperty("created_at") LocalDateTime createdAt
) {

    public static AdminUserDTO from(Auth user) {
        return new AdminUserDTO(
                user.getId(),
                user.getName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().getName(),
                user.isActive(),
                user.getDeactivationReason(),
                user.getCreatedAt()
        );
    }
}
