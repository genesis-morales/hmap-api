package com.hmap.backend.room.controller;

import com.hmap.backend.room.dto.RoomDTO;
import com.hmap.backend.room.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/rooms")
@Tag(name = "Habitaciones", description = "Catálogo público y disponibilidad de habitaciones")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    @Operation(summary = "Listar el catálogo de habitaciones (HU-001/HU-002)")
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
}
