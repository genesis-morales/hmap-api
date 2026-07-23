package com.hmap.backend.reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Datos para crear una reserva (HU-009). */
public record CreateReservationRequest(

        @JsonProperty("room_id")
        @NotNull(message = "La habitación es obligatoria")
        Long roomId,

        @JsonProperty("check_in")
        @NotNull(message = "La fecha de entrada es obligatoria")
        LocalDate checkIn,

        @JsonProperty("check_out")
        @NotNull(message = "La fecha de salida es obligatoria")
        LocalDate checkOut,

        @NotNull(message = "La cantidad de huéspedes es obligatoria")
        @Min(value = 1, message = "La cantidad de huéspedes debe ser al menos 1")
        Integer guests
) {
}
