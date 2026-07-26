package com.hmap.backend.reservation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hmap.backend.reservation.entity.Reservation;
import com.hmap.backend.room.dto.RoomDTO;
import com.hmap.backend.room.support.ImageUrlResolver;

/**
 * Reserva tal como la consume el frontend (contrato E2). Los flags
 * {@code can_edit}/{@code can_cancel} los calcula la API según la política
 * de plazos; el {@code code} se deriva del id (RSV-000123).
 */
public record ReservationDTO(
        Long id,
        String code,
        GuestDTO guest,
        RoomDTO room,
        @JsonProperty("check_in") LocalDate checkIn,
        @JsonProperty("check_out") LocalDate checkOut,
        int guests,
        long nights,
        BigDecimal total,
        String status,
        @JsonProperty("can_edit") boolean canEdit,
        @JsonProperty("can_cancel") boolean canCancel,
        @JsonProperty("created_at") LocalDateTime createdAt
) {

    public static ReservationDTO from(Reservation reservation, boolean canEdit, boolean canCancel,
                                      ImageUrlResolver imageUrlResolver) {
        return new ReservationDTO(
                reservation.getId(),
                "RSV-%06d".formatted(reservation.getId()),
                GuestDTO.from(reservation.getUser()),
                RoomDTO.from(reservation.getRoom(), imageUrlResolver),
                reservation.getCheckIn(),
                reservation.getCheckOut(),
                reservation.getGuests(),
                reservation.getNights(),
                reservation.getTotal(),
                reservation.getStatus().name(),
                canEdit,
                canCancel,
                reservation.getCreatedAt()
        );
    }
}
