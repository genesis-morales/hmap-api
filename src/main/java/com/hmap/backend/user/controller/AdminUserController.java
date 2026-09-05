package com.hmap.backend.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.common.dto.PageResponse;
import com.hmap.backend.role.enums.RoleName;
import com.hmap.backend.user.dto.AdminUserDTO;
import com.hmap.backend.user.dto.CreateUserRequest;
import com.hmap.backend.user.dto.UpdateUserActiveRequest;
import com.hmap.backend.user.dto.UpdateUserRequest;
import com.hmap.backend.user.service.AdminUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Panel de administrador: gestión de cuentas de personal interno (HU-030 a
 * HU-034, RF-015). Todas las rutas exigen rol ADMINISTRADOR (RNF-001).
 */
@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(name = "Administración de usuarios", description = "Gestión de cuentas internas por el administrador")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "Listar usuarios con filtros y paginación (HU-034)")
    public ResponseEntity<PageResponse<AdminUserDTO>> list(
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserService.list(role, active, search, page, size));
    }

    @PostMapping
    @Operation(summary = "Crear una cuenta de personal interno (HU-030/032)")
    public ResponseEntity<AdminUserDTO> create(@RequestBody @Valid CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.create(request));
    }

    // La regex {id:\d+} evita que "me" (UserController) matchee como id.
    @PutMapping("/{id:\\d+}")
    @Operation(summary = "Editar datos y rol de una cuenta (HU-031/032)")
    public ResponseEntity<AdminUserDTO> update(@AuthenticationPrincipal Auth admin,
                                               @PathVariable Long id,
                                               @RequestBody @Valid UpdateUserRequest request) {
        return ResponseEntity.ok(adminUserService.update(admin.getId(), id, request));
    }

    @PatchMapping("/{id:\\d+}/active")
    @Operation(summary = "Activar o suspender una cuenta (HU-033)")
    public ResponseEntity<AdminUserDTO> setActive(@AuthenticationPrincipal Auth admin,
                                                  @PathVariable Long id,
                                                  @RequestBody @Valid UpdateUserActiveRequest request) {
        return ResponseEntity.ok(
                adminUserService.setActive(admin.getId(), id, request.active(), request.observation()));
    }
}
