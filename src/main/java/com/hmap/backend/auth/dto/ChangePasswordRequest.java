package com.hmap.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cambio de contraseña desde el perfil (HU-015). */
public record ChangePasswordRequest(

        @JsonProperty("current_password")
        @NotBlank(message = "La contraseña actual es obligatoria")
        String currentPassword,

        @JsonProperty("new_password")
        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres")
        String newPassword
) {
}
