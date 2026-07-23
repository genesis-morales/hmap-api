package com.hmap.backend.dashboard.controller;

import com.hmap.backend.dashboard.dto.OccupancyDTO;
import com.hmap.backend.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Métricas operativas del panel de recepción")
@PreAuthorize("hasAnyRole('RECEPCIONISTA','ADMINISTRADOR')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/occupancy")
    @Operation(summary = "Resumen de ocupación de habitaciones (HU-016)")
    public ResponseEntity<OccupancyDTO> occupancy() {
        return ResponseEntity.ok(dashboardService.getOccupancy());
    }
}
