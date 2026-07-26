package com.hmap.backend.reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Entradas y salidas programadas para el día actual (HU-018).
 */
public record TodayReservationsDTO(
        @JsonProperty("check_ins") List<ReservationDTO> checkIns,
        @JsonProperty("check_outs") List<ReservationDTO> checkOuts
) {
}
