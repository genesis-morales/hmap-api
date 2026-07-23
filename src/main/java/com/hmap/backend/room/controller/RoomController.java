package com.hmap.backend.room.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hmap.backend.room.dto.RoomDTO;
import com.hmap.backend.room.dto.RoomRequest;
import com.hmap.backend.room.dto.RoomStatusRequest;
import com.hmap.backend.room.service.RoomService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/rooms")
@Tag(name = "Habitaciones", description = "Catálogo público, disponibilidad e inventario interno")
public class RoomController {

    /** Regla de autorización del inventario (RECEPCIONISTA o ADMINISTRADOR). */
    private static final String INTERNAL = "hasAnyRole('RECEPCIONISTA','ADMINISTRADOR')";

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    // === Lectura pública (HU-001/HU-002/HU-008) ===

    @GetMapping
    @Operation(summary = "Listar el catálogo de habitaciones (HU-001/HU-002/HU-029)")
    public ResponseEntity<List<RoomDTO>> findAll() {
        return ResponseEntity.ok(roomService.findAll());
    }

    @GetMapping("/availability")
    @Operation(summary = "Buscar habitaciones disponibles por fechas y huéspedes (HU-008)")
    public ResponseEntity<List<RoomDTO>> availability(
            @RequestParam("check_in") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam("check_out") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam("guests") int guests) {
        return ResponseEntity.ok(roomService.findAvailable(checkIn, checkOut, guests));
    }

    // La regex {id:\d+} evita que "availability" matchee como id.
    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Ver el detalle de una habitación (HU-002)")
    public ResponseEntity<RoomDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.findById(id));
    }

    // === Inventario interno (HU-025 a HU-028) — requiere rol interno ===

    @PostMapping
    @PreAuthorize(INTERNAL)
    @Operation(summary = "Crear una habitación (HU-025)")
    public ResponseEntity<RoomDTO> create(@RequestBody @Valid RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.create(request));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize(INTERNAL)
    @Operation(summary = "Editar una habitación (HU-026)")
    public ResponseEntity<RoomDTO> update(@PathVariable Long id, @RequestBody @Valid RoomRequest request) {
        return ResponseEntity.ok(roomService.update(id, request));
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize(INTERNAL)
    @Operation(summary = "Eliminar una habitación sin reservas activas (HU-027)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roomService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id:\\d+}/status")
    @PreAuthorize(INTERNAL)
    @Operation(summary = "Cambiar el estado de una habitación (HU-028)")
    public ResponseEntity<RoomDTO> changeStatus(@PathVariable Long id,
                                                @RequestBody @Valid RoomStatusRequest request) {
        return ResponseEntity.ok(roomService.changeStatus(id, request));
    }
}
