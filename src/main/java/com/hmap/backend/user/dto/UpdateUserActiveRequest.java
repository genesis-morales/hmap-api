package com.hmap.backend.user.dto;

import jakarta.validation.constraints.NotNull;

/** Activación o suspensión de una cuenta (HU-033). */
public record UpdateUserActiveRequest(

        @NotNull(message = "El estado activo es obligatorio")
        Boolean active
) {
}
