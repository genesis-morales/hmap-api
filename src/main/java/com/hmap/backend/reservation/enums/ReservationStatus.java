package com.hmap.backend.reservation.enums;

/**
 * Estado de una reserva. E3 agregará CHECK_IN y CHECK_OUT
 * (por eso la columna es VARCHAR y no un enum de BD).
 */
public enum ReservationStatus {
    PENDIENTE,
    CONFIRMADA,
    CANCELADA;

    /** Una reserva activa bloquea disponibilidad y admite edición/cancelación. */
    public boolean isActive() {
        return this == PENDIENTE || this == CONFIRMADA;
    }
}
