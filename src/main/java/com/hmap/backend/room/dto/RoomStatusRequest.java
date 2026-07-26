package com.hmap.backend.room.dto;

import jakarta.validation.constraints.NotBlank;

/** Cambio de estado operativo de una habitación (HU-028). */
public record RoomStatusRequest(

        @NotBlank(message = "El estado es obligatorio")
        String status
) {
}
