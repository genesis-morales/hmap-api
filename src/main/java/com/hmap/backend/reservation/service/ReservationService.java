package com.hmap.backend.reservation.service;

import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.exception.BadRequestException;
import com.hmap.backend.exception.ConflictException;
import com.hmap.backend.exception.ForbiddenException;
import com.hmap.backend.exception.ResourceNotFoundException;
import com.hmap.backend.notification.MailService;
import com.hmap.backend.reservation.dto.CreateReservationRequest;
import com.hmap.backend.reservation.dto.ReservationDTO;
import com.hmap.backend.reservation.dto.UpdateReservationRequest;
import com.hmap.backend.reservation.entity.Reservation;
import com.hmap.backend.reservation.enums.ReservationStatus;
import com.hmap.backend.reservation.repository.ReservationRepository;
import com.hmap.backend.reservation.support.StayDates;
import com.hmap.backend.room.entity.Room;
import com.hmap.backend.room.enums.RoomStatus;
import com.hmap.backend.room.repository.RoomRepository;
import com.hmap.backend.room.service.RoomService;
import com.hmap.backend.room.support.ImageUrlResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Motor de reservas del cliente (HU-009 a HU-013). Las reglas de negocio
 * viven aquí; el frontend solo refleja los flags {@code can_edit}/{@code can_cancel}.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    /** Estado con el que nace una reserva creada por el cliente. */
    private static final ReservationStatus INITIAL_STATUS = ReservationStatus.PENDIENTE;

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final MailService mailService;
    private final ImageUrlResolver imageUrlResolver;

    /** Horas antes del check-in hasta las que se permite editar/cancelar. */
    @Value("${app.reservations.edit-window-hours}")
    private long editWindowHours;

    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              MailService mailService,
                              ImageUrlResolver imageUrlResolver) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.mailService = mailService;
        this.imageUrlResolver = imageUrlResolver;
    }

    /** Crea una reserva re-validando disponibilidad bajo bloqueo (HU-009). */
    @Transactional
    public ReservationDTO create(Auth user, CreateReservationRequest request) {
        StayDates.validate(request.checkIn(), request.checkOut());

        // FOR UPDATE: serializa las reservas concurrentes sobre la misma habitación
        var room = roomRepository.findWithLockById(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Habitación no encontrada"));

        validateRoomAndCapacity(room, request.guests());

        if (reservationRepository.existsOverlapping(room.getId(), request.checkIn(),
                request.checkOut(), RoomService.ACTIVE_STATUSES, null)) {
            throw new ConflictException("La habitación ya no está disponible en esas fechas.");
        }

        var reservation = Reservation.builder()
                .user(user)
                .room(room)
                .checkIn(request.checkIn())
                .checkOut(request.checkOut())
                .guests(request.guests())
                .total(calculateTotal(room, request.checkIn(), request.checkOut()))
                .status(INITIAL_STATUS)
                .build();

        reservationRepository.save(reservation);

        sendConfirmationEmail(user, reservation);

        return toDto(reservation);
    }

    /** Reservas del usuario autenticado, más recientes primero (HU-010). */
    @Transactional(readOnly = true)
    public List<ReservationDTO> findMine(Auth user) {
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toDto).toList();
    }

    /** Detalle de una reserva propia; 403 si pertenece a otro usuario (HU-011). */
    @Transactional(readOnly = true)
    public ReservationDTO findById(Auth user, Long id) {
        return toDto(getOwnedReservation(user, id));
    }

    /** Edita fechas/huéspedes dentro de la ventana permitida (HU-012). */
    @Transactional
    public ReservationDTO update(Auth user, Long id, UpdateReservationRequest request) {
        var reservation = getOwnedReservation(user, id);

        if (!isWithinEditWindow(reservation)) {
            throw new ConflictException("La reserva ya no puede modificarse");
        }

        StayDates.validate(request.checkIn(), request.checkOut());

        // Mismo bloqueo que al crear: protege contra carreras con otras reservas
        var room = roomRepository.findWithLockById(reservation.getRoom().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Habitación no encontrada"));

        validateRoomAndCapacity(room, request.guests());

        if (reservationRepository.existsOverlapping(room.getId(), request.checkIn(),
                request.checkOut(), RoomService.ACTIVE_STATUSES, reservation.getId())) {
            throw new ConflictException("La habitación ya no está disponible en esas fechas.");
        }

        reservation.setCheckIn(request.checkIn());
        reservation.setCheckOut(request.checkOut());
        reservation.setGuests(request.guests());
        reservation.setTotal(calculateTotal(room, request.checkIn(), request.checkOut()));
        reservationRepository.save(reservation);

        return toDto(reservation);
    }

    /** Cancela la reserva dentro de la ventana permitida (HU-013). */
    @Transactional
    public ReservationDTO cancel(Auth user, Long id) {
        var reservation = getOwnedReservation(user, id);

        if (!isWithinEditWindow(reservation)) {
            throw new ConflictException("La reserva ya no puede cancelarse");
        }

        reservation.setStatus(ReservationStatus.CANCELADA);
        reservationRepository.save(reservation);

        sendCancellationEmail(user, reservation);

        return toDto(reservation);
    }

    // === Reglas internas ===

    private Reservation getOwnedReservation(Auth user, Long id) {
        var reservation = reservationRepository.findWithRoomById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        // 403 (no 401): el frontend cierra la sesión ante cualquier 401
        if (!reservation.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("No tienes permiso sobre esta reserva");
        }
        return reservation;
    }

    private void validateRoomAndCapacity(Room room, int guests) {
        if (room.getStatus() == RoomStatus.MANTENIMIENTO) {
            throw new ConflictException("La habitación no está disponible actualmente");
        }
        if (guests > room.getCapacity()) {
            throw new BadRequestException(
                    "La habitación admite hasta %d huéspedes".formatted(room.getCapacity()));
        }
    }

    private BigDecimal calculateTotal(Room room, LocalDate checkIn, LocalDate checkOut) {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        return room.getPricePerNight().multiply(BigDecimal.valueOf(nights));
    }

    /**
     * Política de plazos (HU-012/HU-013): editar/cancelar se permite solo en
     * estado activo y hasta {@code editWindowHours} horas antes del check-in.
     */
    private boolean isWithinEditWindow(Reservation reservation) {
        return reservation.getStatus().isActive()
                && LocalDateTime.now().isBefore(
                        reservation.getCheckIn().atStartOfDay().minusHours(editWindowHours));
    }

    private ReservationDTO toDto(Reservation reservation) {
        boolean editable = isWithinEditWindow(reservation);
        return ReservationDTO.from(reservation, editable, editable, imageUrlResolver);
    }

    // === Correos (best-effort: un fallo de SMTP no debe romper la operación) ===

    private void sendConfirmationEmail(Auth user, Reservation reservation) {
        try {
            mailService.sendReservationConfirmationEmail(
                    user.getEmail(),
                    user.getName(),
                    "RSV-%06d".formatted(reservation.getId()),
                    reservation.getRoom().getName(),
                    reservation.getCheckIn(),
                    reservation.getCheckOut(),
                    reservation.getGuests(),
                    reservation.getNights(),
                    reservation.getTotal());
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de confirmación de la reserva {}: {}",
                    reservation.getId(), e.getMessage());
        }
    }

    private void sendCancellationEmail(Auth user, Reservation reservation) {
        try {
            mailService.sendReservationCancellationEmail(
                    user.getEmail(),
                    user.getName(),
                    "RSV-%06d".formatted(reservation.getId()),
                    reservation.getRoom().getName(),
                    reservation.getCheckIn(),
                    reservation.getCheckOut());
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de cancelación de la reserva {}: {}",
                    reservation.getId(), e.getMessage());
        }
    }
}
