package com.hmap.backend.reservation.enums;

/**
 * Estado de una reserva a lo largo de su ciclo de vida. La columna es VARCHAR
 * (no un enum de BD), por lo que agregar estados no requiere migración.
 *
 * <pre>
 * PENDIENTE  -> el cliente crea la reserva (aún no paga)
 * CONFIRMADA -> el recepcionista la marca al llegar y pagar el huésped
 * CHECK_IN   -> el huésped ingresa; la habitación pasa a OCUPADA
 * CHECK_OUT  -> el huésped se retira; la habitación vuelve a DISPONIBLE
 * CANCELADA  -> anulación (nunca borra el registro)
 * </pre>
 */
public enum ReservationStatus {
    PENDIENTE,
    CONFIRMADA,
    CHECK_IN,
    CHECK_OUT,
    CANCELADA;

    /**
     * Activa para el cliente: solo estas admiten edición/cancelación por su parte
     * dentro de la ventana de plazos (HU-012/HU-013). Una reserva ya con check-in
     * no la edita el cliente.
     */
    public boolean isActive() {
        return this == PENDIENTE || this == CONFIRMADA;
    }

    /**
     * Bloquea la disponibilidad de la habitación: además de las activas, una
     * reserva con {@code CHECK_IN} sigue ocupando el cuarto. {@code CHECK_OUT} y
     * {@code CANCELADA} ya no bloquean.
     */
    public boolean blocksAvailability() {
        return this == PENDIENTE || this == CONFIRMADA || this == CHECK_IN;
    }
}
