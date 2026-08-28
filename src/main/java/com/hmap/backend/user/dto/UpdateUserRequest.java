package com.hmap.backend.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hmap.backend.role.enums.RoleName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos editables de una cuenta interna desde el panel admin (HU-031/032). El
 * correo (identidad de acceso) y la contraseña no se modifican aquí. Reasignar
 * el rol está permitido salvo que el admin intente quitarse el suyo (regla en
 * el servicio).
 */
public record UpdateUserRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
        String name,

        @JsonProperty("last_name")
        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 120, message = "El apellido no puede superar los 120 caracteres")
        String lastName,

        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
        String phone,

        @NotNull(message = "El rol es obligatorio")
        RoleName role
) {
}
