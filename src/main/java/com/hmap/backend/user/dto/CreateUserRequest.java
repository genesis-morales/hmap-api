package com.hmap.backend.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hmap.backend.role.enums.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear una cuenta de personal interno (HU-030/032). El rol debe ser
 * asignable (RECEPCIONISTA o ADMINISTRADOR); la validación de negocio vive en el
 * servicio. La cuenta nace activa.
 */
public record CreateUserRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
        String name,

        @JsonProperty("last_name")
        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 120, message = "El apellido no puede superar los 120 caracteres")
        String lastName,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 150, message = "El correo no puede superar los 150 caracteres")
        String email,

        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
        String phone,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
        String password,

        @NotNull(message = "El rol es obligatorio")
        RoleName role
) {
}
