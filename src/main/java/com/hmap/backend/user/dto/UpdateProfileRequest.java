package com.hmap.backend.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos editables del perfil del usuario (HU-014). El email no se modifica. */
public record UpdateProfileRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
        String name,

        @JsonProperty("last_name")
        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 120, message = "El apellido no puede superar los 120 caracteres")
        String lastName,

        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
        String phone
) {
}
