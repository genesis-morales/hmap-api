package com.hmap.backend.reservation.controller;

import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.reservation.dto.CreateReservationRequest;
import com.hmap.backend.reservation.dto.ReservationDTO;
import com.hmap.backend.reservation.dto.UpdateReservationRequest;
import com.hmap.backend.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reservations")
@Tag(name = "Reservas", description = "Reservas del cliente autenticado")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

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

    // La regex {id:\d+} evita que "me" matchee como id.
    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Ver el detalle de una reserva propia (HU-011)")
    public ResponseEntity<ReservationDTO> findById(@AuthenticationPrincipal Auth user,
                                                   @PathVariable Long id) {
        return ResponseEntity.ok(reservationService.findById(user, id));
    }

    @PutMapping("/{id:\\d+}")
    @Operation(summary = "Editar fechas o huéspedes de una reserva (HU-012)")
    public ResponseEntity<ReservationDTO> update(@AuthenticationPrincipal Auth user,
                                                 @PathVariable Long id,
                                                 @RequestBody @Valid UpdateReservationRequest request) {
        return ResponseEntity.ok(reservationService.update(user, id, request));
    }

    @PostMapping("/{id:\\d+}/cancel")
    @Operation(summary = "Cancelar una reserva (HU-013)")
    public ResponseEntity<ReservationDTO> cancel(@AuthenticationPrincipal Auth user,
                                                 @PathVariable Long id) {
        return ResponseEntity.ok(reservationService.cancel(user, id));
    }
}
