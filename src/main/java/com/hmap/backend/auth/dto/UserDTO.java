package com.hmap.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hmap.backend.auth.entity.Auth;

/**
 * Datos del usuario autenticado que consume el frontend en GET /auth/me.
 * El campo {@code role} usa el literal en mayúsculas del rol
 * (CLIENTE, RECEPCIONISTA o ADMINISTRADOR).
 */
public record UserDTO(
        Long id,
        String name,
        @JsonProperty("last_name") String lastName,
        String email,
        String role
) {

    public static UserDTO from(Auth user) {
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole().getName()
        );
    }
}
