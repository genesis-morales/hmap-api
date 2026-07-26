package com.hmap.backend.reception.controller;

import com.hmap.backend.reception.dto.OccupancyDTO;
import com.hmap.backend.reception.service.ReceptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/panel-reception")
@Tag(name = "Panel de Recepción", description = "Métricas operativas del panel de recepción")
@PreAuthorize("hasAnyRole('RECEPCIONISTA','ADMINISTRADOR')")
public class ReceptionController {

    private final ReceptionService receptionService;

    public ReceptionController(ReceptionService receptionService) {
        this.receptionService = receptionService;
    }

    @GetMapping("/occupancy")
    @Operation(summary = "Resumen de ocupación de habitaciones (HU-016)")
    public ResponseEntity<OccupancyDTO> occupancy() {
        return ResponseEntity.ok(receptionService.getOccupancy());
    }
}
