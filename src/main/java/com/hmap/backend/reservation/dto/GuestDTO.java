package com.hmap.backend.reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hmap.backend.auth.entity.Auth;

/**
 * Datos del huésped titular de una reserva. Se anida en {@link ReservationDTO}
 * para que el panel de recepción muestre a quién pertenece cada reserva sin
 * pedir el usuario aparte (HU-023/024). Para el cliente es su propia cuenta.
 */
public record GuestDTO(
        Long id,
        String name,
        @JsonProperty("last_name") String lastName,
        String email,
        String phone
) {

    public static GuestDTO from(Auth user) {
        return new GuestDTO(
                user.getId(),
                user.getName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone()
        );
    }
}
