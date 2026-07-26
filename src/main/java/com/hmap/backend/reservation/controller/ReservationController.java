package com.hmap.backend.reservation.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.common.dto.PageResponse;
import com.hmap.backend.reservation.dto.CancelReservationRequest;
import com.hmap.backend.reservation.dto.CreateReservationRequest;
import com.hmap.backend.reservation.dto.ManualReservationRequest;
import com.hmap.backend.reservation.dto.ReservationDTO;
import com.hmap.backend.reservation.dto.TodayReservationsDTO;
import com.hmap.backend.reservation.dto.UpdateReservationRequest;
import com.hmap.backend.reservation.enums.ReservationStatus;
import com.hmap.backend.reservation.service.ReservationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/reservations")
@Tag(name = "Reservas", description = "Reservas del cliente y operación del panel de recepción")
public class ReservationController {

    /** Regla de autorización de las rutas internas (RECEPCIONISTA o ADMINISTRADOR). */
    private static final String INTERNAL = "hasAnyRole('RECEPCIONISTA','ADMINISTRADOR')";

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // === Cliente (HU-009 a HU-013) ===

    @PostMapping
    @Operation(summary = "Crear una reserva (HU-009)")
    public ResponseEntity<ReservationDTO> create(@AuthenticationPrincipal Auth user,
                                                 @RequestBody @Valid CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.create(user, request));
    }

    @GetMapping("/me")
    @Operation(summary = "Listar mis reservas (HU-010)")
    public ResponseEntity<List<ReservationDTO>> findMine(@AuthenticationPrincipal Auth user) {
        return ResponseEntity.ok(reservationService.findMine(user));
    }

    // La regex {id:\d+} evita que "me", "today" o "calendar" matcheen como id.
    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Ver el detalle de una reserva propia (HU-011)")
    public ResponseEntity<ReservationDTO> findById(@AuthenticationPrincipal Auth user,
                                                   @PathVariable Long id) {
        return ResponseEntity.ok(reservationService.findById(user, id));
    }

    /** Editar: el cliente sujeto a la ventana (HU-012); el rol interno sin ella (HU-021). */
    @PutMapping("/{id:\\d+}")
    @Operation(summary = "Editar fechas o huéspedes de una reserva (HU-012 / HU-021)")
    public ResponseEntity<ReservationDTO> update(@AuthenticationPrincipal Auth user,
                                                 @PathVariable Long id,
                                                 @RequestBody @Valid UpdateReservationRequest request) {
        return ResponseEntity.ok(reservationService.update(user, id, request));
    }

    /** Cancelar: el cliente sin motivo (HU-013); el rol interno con {@code reason} obligatorio (HU-022). */
    @PostMapping("/{id:\\d+}/cancel")
    @Operation(summary = "Cancelar una reserva (HU-013 / HU-022)")
    public ResponseEntity<ReservationDTO> cancel(@AuthenticationPrincipal Auth user,
                                                 @PathVariable Long id,
                                                 @RequestBody(required = false) CancelReservationRequest request) {
        return ResponseEntity.ok(reservationService.cancel(user, id, request));
    }

    // === Recepción (HU-017 a HU-024) — requiere rol interno ===

    @GetMapping
    @PreAuthorize(INTERNAL)
    @Operation(summary = "Tabla global de reservas con filtros y paginación (HU-023/024)")
    public ResponseEntity<PageResponse<ReservationDTO>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reservationService.search(search, from, to, status, page, size));
    }

    @GetMapping("/today")
    @PreAuthorize(INTERNAL)
    @Operation(summary = "Check-ins y check-outs del día (HU-018)")
    public ResponseEntity<TodayReservationsDTO> today() {
        return ResponseEntity.ok(reservationService.findToday());
    }

    @GetMapping("/calendar")
    @PreAuthorize(INTERNAL)
    @Operation(summary = "Reservas del calendario en un rango de fechas (HU-017)")
    public ResponseEntity<List<ReservationDTO>> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reservationService.findCalendar(from, to));
    }

    @PostMapping("/manual")
    @PreAuthorize(INTERNAL)
    @Operation(summary = "Crear una reserva a nombre de un cliente (HU-020)")
    public ResponseEntity<ReservationDTO> createManual(@RequestBody @Valid ManualReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createManual(request));
    }

    @PostMapping("/{id:\\d+}/confirm")
    @PreAuthorize(INTERNAL)
    @Operation(summary = "Confirmar una reserva pendiente (pago/llegada del huésped)")
    public ResponseEntity<ReservationDTO> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.confirm(id));
    }

    @PostMapping("/{id:\\d+}/check-in")
    @PreAuthorize(INTERNAL)
    @Operation(summary = "Registrar el ingreso del huésped (HU-019)")
    public ResponseEntity<ReservationDTO> checkIn(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.checkIn(id));
    }

    @PostMapping("/{id:\\d+}/check-out")
    @PreAuthorize(INTERNAL)
    @Operation(summary = "Registrar la salida del huésped (HU-019)")
    public ResponseEntity<ReservationDTO> checkOut(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.checkOut(id));
    }
}
