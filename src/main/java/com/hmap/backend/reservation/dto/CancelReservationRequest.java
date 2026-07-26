package com.hmap.backend.reservation.dto;

/**
 * Cuerpo opcional al cancelar una reserva. Para el rol interno (recepción) el
 * {@code reason} es obligatorio (HU-022); para el cliente es irrelevante y el
 * cuerpo puede omitirse (HU-013).
 */
public record CancelReservationRequest(String reason) {
}
