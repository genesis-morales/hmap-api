package com.hmap.backend.reservation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.common.dto.PageRequests;
import com.hmap.backend.common.dto.PageResponse;
import com.hmap.backend.exception.BadRequestException;
import com.hmap.backend.exception.ConflictException;
import com.hmap.backend.exception.ForbiddenException;
import com.hmap.backend.exception.ResourceNotFoundException;
import com.hmap.backend.notification.MailService;
import com.hmap.backend.reservation.dto.CancelReservationRequest;
import com.hmap.backend.reservation.dto.CreateReservationRequest;
import com.hmap.backend.reservation.dto.ManualReservationRequest;
import com.hmap.backend.reservation.dto.ReservationDTO;
import com.hmap.backend.reservation.dto.TodayReservationsDTO;
import com.hmap.backend.reservation.dto.UpdateReservationRequest;
import com.hmap.backend.reservation.entity.Reservation;
import com.hmap.backend.reservation.enums.ReservationStatus;
import com.hmap.backend.reservation.repository.ReservationRepository;
import com.hmap.backend.reservation.support.StayDates;
import com.hmap.backend.role.enums.RoleName;
import com.hmap.backend.room.entity.Room;
import com.hmap.backend.room.enums.RoomStatus;
import com.hmap.backend.room.repository.RoomRepository;
import com.hmap.backend.room.service.RoomService;
import com.hmap.backend.room.support.ImageUrlResolver;
import com.hmap.backend.user.service.UserService;

/**
 * Motor de reservas. Reúne las operaciones del cliente (HU-009 a HU-013, con
 * propiedad y ventana de plazos) y las de recepción (HU-017 a HU-024, sobre
 * cualquier reserva y sin ventana). Las reglas de negocio viven aquí; el
 * frontend solo refleja los flags {@code can_edit}/{@code can_cancel}.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    /** Estado con el que nace una reserva (creada por el cliente o por recepción). */
    private static final ReservationStatus INITIAL_STATUS = ReservationStatus.PENDIENTE;

    /** Roles que operan el panel interno (sin propiedad ni ventana de plazos). */
    private static final Set<String> INTERNAL_ROLES =
            Set.of(RoleName.RECEPCIONISTA.name(), RoleName.ADMINISTRADOR.name());

    /** Reservas que aún no han hecho check-in y llegan hoy (HU-018). */
    private static final Set<ReservationStatus> ARRIVING_STATUSES =
            EnumSet.of(ReservationStatus.PENDIENTE, ReservationStatus.CONFIRMADA);

    /** Reservas hospedadas que salen hoy (HU-018). */
    private static final Set<ReservationStatus> DEPARTING_STATUSES =
            EnumSet.of(ReservationStatus.CHECK_IN);

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final MailService mailService;
    private final ImageUrlResolver imageUrlResolver;
    private final UserService userService;

    /** Horas antes del check-in hasta las que el cliente puede editar/cancelar. */
    @Value("${app.reservations.edit-window-hours}")
    private long editWindowHours;

    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              MailService mailService,
                              ImageUrlResolver imageUrlResolver,
                              UserService userService) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.mailService = mailService;
        this.imageUrlResolver = imageUrlResolver;
        this.userService = userService;
    }

    // === Operaciones del cliente (HU-009 a HU-013) ===

    /** Crea una reserva re-validando disponibilidad bajo bloqueo (HU-009). */
    @Transactional
    public ReservationDTO create(Auth user, CreateReservationRequest request) {
        StayDates.validate(request.checkIn(), request.checkOut());

        // FOR UPDATE: serializa las reservas concurrentes sobre la misma habitación
        var room = roomRepository.findWithLockById(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Habitación no encontrada"));

        validateRoomAndCapacity(room, request.guests());
        ensureRangeAvailable(room, request.checkIn(), request.checkOut(), null);

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
        sendConfirmationEmail(reservation);

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

    /**
     * Edita fechas/huéspedes. El cliente solo dentro de la ventana y sobre su
     * propia reserva (HU-012); el rol interno sobre cualquier reserva activa y
     * sin ventana (HU-021).
     */
    @Transactional
    public ReservationDTO update(Auth actor, Long id, UpdateReservationRequest request) {
        boolean internal = isInternal(actor);
        var reservation = internal ? getReservation(id) : getOwnedReservation(actor, id);

        if (internal) {
            if (!reservation.getStatus().isActive()) {
                throw new ConflictException("La reserva ya no puede modificarse");
            }
        } else if (!isWithinEditWindow(reservation)) {
            throw new ConflictException("La reserva ya no puede modificarse");
        }

        StayDates.validate(request.checkIn(), request.checkOut());

        // Mismo bloqueo que al crear: protege contra carreras con otras reservas
        var room = roomRepository.findWithLockById(reservation.getRoom().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Habitación no encontrada"));

        validateRoomAndCapacity(room, request.guests());
        ensureRangeAvailable(room, request.checkIn(), request.checkOut(), reservation.getId());

        reservation.setCheckIn(request.checkIn());
        reservation.setCheckOut(request.checkOut());
        reservation.setGuests(request.guests());
        reservation.setTotal(calculateTotal(room, request.checkIn(), request.checkOut()));
        reservationRepository.save(reservation);

        return toDto(reservation);
    }

    /**
     * Cancela una reserva (nunca la elimina, solo cambia el estado). El cliente
     * solo dentro de la ventana (HU-013); el rol interno con un motivo obligatorio
     * y sobre cualquier reserva activa (HU-022).
     */
    @Transactional
    public ReservationDTO cancel(Auth actor, Long id, CancelReservationRequest request) {
        boolean internal = isInternal(actor);
        var reservation = internal ? getReservation(id) : getOwnedReservation(actor, id);

        if (internal) {
            if (request == null || request.reason() == null || request.reason().isBlank()) {
                throw new BadRequestException("El motivo de la cancelación es obligatorio", "reason");
            }
            if (!reservation.getStatus().isActive()) {
                throw new ConflictException("La reserva ya no puede cancelarse");
            }
        } else if (!isWithinEditWindow(reservation)) {
            throw new ConflictException("La reserva ya no puede cancelarse");
        }

        reservation.setStatus(ReservationStatus.CANCELADA);
        reservationRepository.save(reservation);
        sendCancellationEmail(reservation);

        return toDto(reservation);
    }

    // === Operaciones de recepción (HU-017 a HU-024) ===

    /** Confirma una reserva pendiente cuando el huésped llega y paga. */
    @Transactional
    public ReservationDTO confirm(Long id) {
        var reservation = getReservation(id);
        if (reservation.getStatus() != ReservationStatus.PENDIENTE) {
            throw new ConflictException("Solo se puede confirmar una reserva pendiente");
        }
        reservation.setStatus(ReservationStatus.CONFIRMADA);
        reservationRepository.save(reservation);
        return toDto(reservation);
    }

    /** Registra el ingreso del huésped: la habitación pasa a OCUPADA (HU-019). */
    @Transactional
    public ReservationDTO checkIn(Long id) {
        var reservation = getReservation(id);
        if (reservation.getStatus() != ReservationStatus.CONFIRMADA) {
            throw new ConflictException("Solo se puede registrar el ingreso de una reserva confirmada");
        }
        reservation.setStatus(ReservationStatus.CHECK_IN);
        reservation.getRoom().setStatus(RoomStatus.OCUPADA);
        reservationRepository.save(reservation);
        return toDto(reservation);
    }

    /** Registra la salida del huésped: la habitación vuelve a DISPONIBLE (HU-019). */
    @Transactional
    public ReservationDTO checkOut(Long id) {
        var reservation = getReservation(id);
        if (reservation.getStatus() != ReservationStatus.CHECK_IN) {
            throw new ConflictException("Solo se puede registrar la salida de una reserva con check-in");
        }
        reservation.setStatus(ReservationStatus.CHECK_OUT);
        reservation.getRoom().setStatus(RoomStatus.DISPONIBLE);
        reservationRepository.save(reservation);
        return toDto(reservation);
    }

    /** Entradas y salidas programadas para hoy (HU-018). */
    @Transactional(readOnly = true)
    public TodayReservationsDTO findToday() {
        var today = LocalDate.now();
        var checkIns = reservationRepository
                .findByCheckInAndStatusInOrderByCheckInAsc(today, ARRIVING_STATUSES)
                .stream().map(this::toDto).toList();
        var checkOuts = reservationRepository
                .findByCheckOutAndStatusInOrderByCheckOutAsc(today, DEPARTING_STATUSES)
                .stream().map(this::toDto).toList();
        return new TodayReservationsDTO(checkIns, checkOuts);
    }

    /** Reservas que tocan el rango del calendario (HU-017). */
    @Transactional(readOnly = true)
    public List<ReservationDTO> findCalendar(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("El rango de fechas es obligatorio");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("La fecha final no puede ser anterior a la inicial");
        }
        return reservationRepository.findForCalendar(from, to)
                .stream().map(this::toDto).toList();
    }

    /** Tabla global paginada con filtros opcionales (HU-023/024). */
    @Transactional(readOnly = true)
    public PageResponse<ReservationDTO> search(String search, LocalDate from, LocalDate to,
                                               ReservationStatus status, int page, int size) {
        var normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        var pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        var result = reservationRepository.search(
                normalizedSearch, parseSearchId(normalizedSearch), from, to, status, pageable);

        return PageResponse.from(result, this::toDto);
    }

    /**
     * Crea una reserva a nombre de un cliente (HU-020). Resuelve o crea la cuenta
     * del huésped y le envía los detalles por correo (HU-037).
     */
    @Transactional
    public ReservationDTO createManual(ManualReservationRequest request) {
        StayDates.validate(request.checkIn(), request.checkOut());

        var guest = request.guest();
        var provisioned = userService.findOrCreateGuest(
                guest.name(), guest.lastName(), guest.email(), guest.phone());

        var room = roomRepository.findWithLockById(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Habitación no encontrada"));

        validateRoomAndCapacity(room, request.guests());
        ensureRangeAvailable(room, request.checkIn(), request.checkOut(), null);

        var reservation = Reservation.builder()
                .user(provisioned.user())
                .room(room)
                .checkIn(request.checkIn())
                .checkOut(request.checkOut())
                .guests(request.guests())
                .total(calculateTotal(room, request.checkIn(), request.checkOut()))
                .status(INITIAL_STATUS)
                .build();

        reservationRepository.save(reservation);
        sendManualReservationEmail(reservation, provisioned.temporaryPassword());

        return toDto(reservation);
    }

    // === Reglas internas ===

    private Reservation getOwnedReservation(Auth user, Long id) {
        var reservation = getReservation(id);

        // 403 (no 401): el frontend cierra la sesión ante cualquier 401
        if (!reservation.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("No tienes permiso sobre esta reserva");
        }
        return reservation;
    }

    private Reservation getReservation(Long id) {
        return reservationRepository.findWithRoomById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
    }

    private boolean isInternal(Auth actor) {
        return INTERNAL_ROLES.contains(actor.getRole().getName());
    }

    private void validateRoomAndCapacity(Room room, int guests) {
        if (room.getStatus() == RoomStatus.MANTENIMIENTO) {
            throw new ConflictException("La habitación no está disponible actualmente");
        }
        if (guests > room.getCapacity()) {
            throw new BadRequestException(
                    "La habitación admite hasta %d huéspedes".formatted(room.getCapacity()), "guests");
        }
    }

    /** Re-valida el solape bajo bloqueo; {@code excludeId} omite la propia reserva al editar. */
    private void ensureRangeAvailable(Room room, LocalDate checkIn, LocalDate checkOut, Long excludeId) {
        if (reservationRepository.existsOverlapping(room.getId(), checkIn, checkOut,
                RoomService.BLOCKING_STATUSES, excludeId)) {
            throw new ConflictException("La habitación ya no está disponible en esas fechas.", "check_in");
        }
    }

    private BigDecimal calculateTotal(Room room, LocalDate checkIn, LocalDate checkOut) {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        return room.getPricePerNight().multiply(BigDecimal.valueOf(nights));
    }

    /**
     * Política de plazos del cliente (HU-012/HU-013): editar/cancelar se permite
     * solo en estado activo y hasta {@code editWindowHours} horas antes del check-in.
     */
    private boolean isWithinEditWindow(Reservation reservation) {
        return reservation.getStatus().isActive()
                && LocalDateTime.now().isBefore(
                        reservation.getCheckIn().atStartOfDay().minusHours(editWindowHours));
    }

    /** Interpreta el término de búsqueda como id/código de reserva (RSV-000123). */
    private Long parseSearchId(String search) {
        if (search == null) {
            return null;
        }
        var digits = search.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ReservationDTO toDto(Reservation reservation) {
        boolean editable = isWithinEditWindow(reservation);
        return ReservationDTO.from(reservation, editable, editable, imageUrlResolver);
    }

    // === Correos (best-effort: un fallo de SMTP no debe romper la operación) ===

    private void sendConfirmationEmail(Reservation reservation) {
        var guest = reservation.getUser();
        try {
            mailService.sendReservationConfirmationEmail(
                    guest.getEmail(),
                    guest.getName(),
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

    private void sendManualReservationEmail(Reservation reservation, String temporaryPassword) {
        var guest = reservation.getUser();
        try {
            mailService.sendManualReservationEmail(
                    guest.getEmail(),
                    guest.getName(),
                    "RSV-%06d".formatted(reservation.getId()),
                    reservation.getRoom().getName(),
                    reservation.getCheckIn(),
                    reservation.getCheckOut(),
                    reservation.getGuests(),
                    reservation.getNights(),
                    reservation.getTotal(),
                    temporaryPassword);
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de la reserva manual {}: {}",
                    reservation.getId(), e.getMessage());
        }
    }

    private void sendCancellationEmail(Reservation reservation) {
        var guest = reservation.getUser();
        try {
            mailService.sendReservationCancellationEmail(
                    guest.getEmail(),
                    guest.getName(),
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
