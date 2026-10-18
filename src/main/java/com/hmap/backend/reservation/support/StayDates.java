package com.hmap.backend.reservation.support;

import com.hmap.backend.exception.BadRequestException;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Validación de rangos de estancia, compartida entre la búsqueda de
 * disponibilidad (RoomService) y el motor de reservas (ReservationService).
 */
public final class StayDates {

    /**
     * Zona horaria del hotel (Costa Rica, UTC-6).
     * Todas las validaciones de fechas se hacen con respecto al "hoy" del hotel.
     */
    private static final ZoneId HOTEL_ZONE = ZoneId.of("America/Costa_Rica");

    private StayDates() {
    }

    /**
     * Reglas del contrato: {@code check_in >= hoy} y {@code check_out > check_in}.
     * El "hoy" se calcula en la zona horaria del hotel.
     */
    public static void validate(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new BadRequestException("Las fechas de entrada y salida son obligatorias", "check_in");
        }
        LocalDate today = LocalDate.now(HOTEL_ZONE);
        if (checkIn.isBefore(today)) {
            throw new BadRequestException("La fecha de entrada no puede ser anterior a hoy", "check_in");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BadRequestException("La fecha de salida debe ser posterior a la de entrada", "check_out");
        }
    }
}
