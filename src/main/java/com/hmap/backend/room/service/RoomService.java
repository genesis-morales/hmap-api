package com.hmap.backend.room.service;

import com.hmap.backend.exception.BadRequestException;
import com.hmap.backend.exception.ResourceNotFoundException;
import com.hmap.backend.reservation.enums.ReservationStatus;
import com.hmap.backend.reservation.support.StayDates;
import com.hmap.backend.room.dto.RoomDTO;
import com.hmap.backend.room.repository.RoomRepository;
import com.hmap.backend.room.support.ImageUrlResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Lectura del catálogo de habitaciones y búsqueda de disponibilidad
 * (HU-002, HU-008 / RF-006).
 */
@Service
public class RoomService {

    /** Estados de reserva que bloquean la disponibilidad de una habitación. */
    public static final Set<ReservationStatus> ACTIVE_STATUSES =
            EnumSet.of(ReservationStatus.PENDIENTE, ReservationStatus.CONFIRMADA);

    private final RoomRepository roomRepository;
    private final ImageUrlResolver imageUrlResolver;

    public RoomService(RoomRepository roomRepository, ImageUrlResolver imageUrlResolver) {
        this.roomRepository = roomRepository;
        this.imageUrlResolver = imageUrlResolver;
    }

    /** Catálogo completo (consumido también por el portal público). */
    @Transactional(readOnly = true)
    public List<RoomDTO> findAll() {
        return roomRepository.findAll().stream()
                .map(room -> RoomDTO.from(room, imageUrlResolver))
                .toList();
    }

    /** Detalle de una habitación. */
    @Transactional(readOnly = true)
    public RoomDTO findById(Long id) {
        var room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Habitación no encontrada"));
        return RoomDTO.from(room, imageUrlResolver);
    }

    /** Habitaciones libres en el rango con capacidad suficiente (HU-008). */
    @Transactional(readOnly = true)
    public List<RoomDTO> findAvailable(LocalDate checkIn, LocalDate checkOut, int guests) {
        StayDates.validate(checkIn, checkOut);
        if (guests < 1) {
            throw new BadRequestException("La cantidad de huéspedes debe ser al menos 1");
        }
        return roomRepository.findAvailable(checkIn, checkOut, guests, ACTIVE_STATUSES)
                .stream()
                .map(room -> RoomDTO.from(room, imageUrlResolver))
                .toList();
    }
}
