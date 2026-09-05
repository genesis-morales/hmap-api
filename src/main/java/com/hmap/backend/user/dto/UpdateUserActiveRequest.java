package com.hmap.backend.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Activación o suspensión de una cuenta (HU-033).
 *
 * <p>La {@code observation} es obligatoria al desactivar, pero no puede
 * declararse con {@code @NotBlank}: su obligatoriedad depende del valor de
 * {@code active}. La regla condicional se aplica en
 * {@link com.hmap.backend.user.service.AdminUserService#setActive}.
 */
public record UpdateUserActiveRequest(

        @NotNull(message = "El estado activo es obligatorio")
        Boolean active,

        @JsonProperty("observation")
        @Size(max = 300, message = "La observación no puede superar los 300 caracteres")
        String observation
) {
}
