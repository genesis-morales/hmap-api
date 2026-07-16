package com.hmap.backend.auth.controller;

import com.hmap.backend.auth.dto.ForgotPasswordRequest;
import com.hmap.backend.auth.dto.LoginRequest;
import com.hmap.backend.auth.dto.RegisterRequest;
import com.hmap.backend.auth.dto.ResetPasswordRequest;
import com.hmap.backend.auth.dto.TokenDTO;
import com.hmap.backend.auth.dto.UserDTO;
import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Registro, inicio de sesión y recuperación de contraseña")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Registrar una nueva cuenta de huésped (HU-003)")
    public ResponseEntity<TokenDTO> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión (HU-004)")
    public ResponseEntity<TokenDTO> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Solicitar enlace de recuperación de contraseña (HU-005)")
    public ResponseEntity<Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Restablecer la contraseña con el token recibido (HU-006)")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener los datos del usuario autenticado")
    public ResponseEntity<UserDTO> me(@AuthenticationPrincipal Auth user) {
        return ResponseEntity.ok(UserDTO.from(user));
    }
}
