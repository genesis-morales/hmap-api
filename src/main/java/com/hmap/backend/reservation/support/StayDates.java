package com.hmap.backend.reservation.support;

import com.hmap.backend.exception.BadRequestException;

import java.time.LocalDate;

/**
 * Validación de rangos de estancia, compartida entre la búsqueda de
 * disponibilidad (RoomService) y el motor de reservas (ReservationService).
 */
public final class StayDates {

    private StayDates() {
    }

    /**
     * Reglas del contrato: {@code check_in >= hoy} y {@code check_out > check_in}.
     */
    public static void validate(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new BadRequestException("Las fechas de entrada y salida son obligatorias", "check_in");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw new BadRequestException("La fecha de entrada no puede ser anterior a hoy", "check_in");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BadRequestException("La fecha de salida debe ser posterior a la de entrada", "check_out");
        }
    }
}
