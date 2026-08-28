package com.hmap.backend.reservation.enums;

/**
 * Origen de la reserva: en línea (cliente desde el portal público) o manual
 * (recepcionista crea la reserva desde el panel interno).
 */
public enum ReservationType {
    /** Cliente reservó desde el portal público (HU-009). */
    ONLINE,

    /** Recepcionista creó la reserva manualmente desde el panel (HU-020). */
    MANUAL
}
