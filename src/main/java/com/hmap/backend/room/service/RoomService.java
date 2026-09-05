package com.hmap.backend.room.service;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hmap.backend.exception.BadRequestException;
import com.hmap.backend.exception.ConflictException;
import com.hmap.backend.exception.ResourceNotFoundException;
import com.hmap.backend.reservation.enums.ReservationStatus;
import com.hmap.backend.reservation.repository.ReservationRepository;
import com.hmap.backend.reservation.support.StayDates;
import com.hmap.backend.room.dto.RoomDTO;
import com.hmap.backend.room.dto.RoomRequest;
import com.hmap.backend.room.dto.RoomStatusRequest;
import com.hmap.backend.room.entity.Room;
import com.hmap.backend.room.enums.RoomStatus;
import com.hmap.backend.room.repository.RoomRepository;
import com.hmap.backend.room.support.ImageUrlResolver;

/**
 * Lectura del catálogo de habitaciones y búsqueda de disponibilidad
 * (HU-002, HU-008 / RF-006), más el mantenimiento del inventario por parte
 * de recepción (HU-025 a HU-029).
 */
@Service
public class RoomService {

    /**
     * Estados de reserva que bloquean la disponibilidad de una habitación:
     * una reserva pendiente, confirmada o con check-in ocupa el cuarto.
     */
    public static final Set<ReservationStatus> BLOCKING_STATUSES =
            EnumSet.of(ReservationStatus.PENDIENTE, ReservationStatus.CONFIRMADA, ReservationStatus.CHECK_IN);

    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final ImageUrlResolver imageUrlResolver;

    public RoomService(RoomRepository roomRepository,
                       ReservationRepository reservationRepository,
                       ImageUrlResolver imageUrlResolver) {
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.imageUrlResolver = imageUrlResolver;
    }

    // === Lectura (E2) ===

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
        return RoomDTO.from(getRoom(id), imageUrlResolver);
    }

    /** Habitaciones libres en el rango con capacidad suficiente (HU-008). */
    @Transactional(readOnly = true)
    public List<RoomDTO> findAvailable(LocalDate checkIn, LocalDate checkOut, int guests) {
        StayDates.validate(checkIn, checkOut);
        if (guests < 1) {
            throw new BadRequestException("La cantidad de huéspedes debe ser al menos 1", "guests");
        }
        return roomRepository.findAvailable(checkIn, checkOut, guests, BLOCKING_STATUSES)
                .stream()
                .map(room -> RoomDTO.from(room, imageUrlResolver))
                .toList();
    }

    // === Inventario (E3) ===

    /** Crea una habitación; el slug debe ser único (HU-025). */
    @Transactional
    public RoomDTO create(RoomRequest request) {
        if (roomRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Ya existe una habitación con ese slug", "slug");
        }
        if (roomRepository.existsByRoomNumber(request.roomNumber())) {
            throw new ConflictException("Ya existe una habitación con ese número", "room_number");
        }

        // builder().build() inicializa las colecciones (@Builder.Default) como listas
        // mutables, necesarias para replaceCollection().
        var room = Room.builder().build();
        applyRequest(room, request);
        room.setStatus(request.status() == null ? RoomStatus.DISPONIBLE : parseStatus(request.status()));

        roomRepository.save(room);
        return RoomDTO.from(room, imageUrlResolver);
    }

    /** Edita una habitación existente (HU-026). */
    @Transactional
    public RoomDTO update(Long id, RoomRequest request) {
        var room = getRoom(id);

        if (roomRepository.existsBySlugAndIdNot(request.slug(), id)) {
            throw new ConflictException("Ya existe una habitación con ese slug", "slug");
        }
        if (roomRepository.existsByRoomNumberAndIdNot(request.roomNumber(), id)) {
            throw new ConflictException("Ya existe una habitación con ese número", "room_number");
        }

        applyRequest(room, request);
        // El estado solo cambia si viene explícito; su endpoint dedicado es HU-028.
        if (request.status() != null) {
            room.setStatus(parseStatus(request.status()));
        }

        roomRepository.save(room);
        return RoomDTO.from(room, imageUrlResolver);
    }

    /** Elimina una habitación sin reservas activas (HU-027). */
    @Transactional
    public void delete(Long id) {
        var room = getRoom(id);

        if (reservationRepository.existsByRoomIdAndStatusIn(id, BLOCKING_STATUSES)) {
            throw new ConflictException("No se puede eliminar una habitación con reservas activas");
        }

        roomRepository.delete(room);
    }

    /** Cambia el estado operativo de una habitación (HU-028). */
    @Transactional
    public RoomDTO changeStatus(Long id, RoomStatusRequest request) {
        var room = getRoom(id);
        room.setStatus(parseStatus(request.status()));
        roomRepository.save(room);
        return RoomDTO.from(room, imageUrlResolver);
    }

    // === Reglas internas ===

    private Room getRoom(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Habitación no encontrada"));
    }

    /** Copia los campos escalares y las colecciones del request a la entidad. */
    private void applyRequest(Room room, RoomRequest request) {
        room.setSlug(request.slug());
        room.setRoomNumber(request.roomNumber());
        room.setName(request.name());
        room.setDescription(request.description());
        room.setCapacity(request.capacity());
        room.setArea(request.area());
        room.setBedsLabel(request.bedsLabel());
        room.setPricePerNight(request.pricePerNight());
        room.setSmokingPolicy(request.smokingPolicy());
        replaceCollection(room.getImages(), request.images());
        replaceCollection(room.getAmenities(), request.amenities());
        replaceCollection(room.getBathroom(), request.bathroom());
        replaceCollection(room.getViews(), request.views());
    }

    /** Sustituye el contenido de una colección gestionada sin cambiar la referencia. */
    private void replaceCollection(List<String> target, List<String> source) {
        target.clear();
        if (source != null) {
            target.addAll(source);
        }
    }

    private RoomStatus parseStatus(String status) {
        try {
            return RoomStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Estado de habitación inválido: " + status, "status");
        }
    }
}
