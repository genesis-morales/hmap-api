package com.hmap.backend.reception.dto;

/**
 * Resumen de ocupación de habitaciones en tiempo real (HU-016).
 */
public record OccupancyDTO(
        long occupied,
        long available,
        long maintenance,
        long total
) {
}
