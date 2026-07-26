package com.hmap.backend.reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Datos para que el recepcionista cree una reserva a nombre de un cliente
 * (HU-020). Si el correo del huésped no existe, se crea una cuenta CLIENTE
 * con contraseña temporal y se le notifica por correo (HU-037).
 */
public record ManualReservationRequest(

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
        Integer guests,

        @NotNull(message = "Los datos del huésped son obligatorios")
        @Valid
        Guest guest
) {

    /** Datos de contacto del huésped titular de la reserva. */
    public record Guest(

            @NotBlank(message = "El nombre del huésped es obligatorio")
            @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
            String name,

            @JsonProperty("last_name")
            @NotBlank(message = "El apellido del huésped es obligatorio")
            @Size(max = 120, message = "El apellido no puede superar los 120 caracteres")
            String lastName,

            @NotBlank(message = "El correo del huésped es obligatorio")
            @Email(message = "El correo no tiene un formato válido")
            @Size(max = 150, message = "El correo no puede superar los 150 caracteres")
            String email,

            @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
            String phone
    ) {
    }
}
