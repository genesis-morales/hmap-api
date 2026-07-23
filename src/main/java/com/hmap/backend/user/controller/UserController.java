package com.hmap.backend.user.controller;

import com.hmap.backend.auth.dto.UserDTO;
import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.user.dto.UpdateProfileRequest;
import com.hmap.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "Usuarios", description = "Gestión del perfil del usuario")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/me")
    @Operation(summary = "Actualizar los datos de contacto del perfil (HU-014)")
    public ResponseEntity<UserDTO> updateProfile(@AuthenticationPrincipal Auth user,
                                                 @RequestBody @Valid UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(user.getId(), request));
    }
}
