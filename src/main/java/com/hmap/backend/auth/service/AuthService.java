package com.hmap.backend.auth.service;

import com.hmap.backend.auth.dto.ChangePasswordRequest;
import com.hmap.backend.auth.dto.ForgotPasswordRequest;
import com.hmap.backend.auth.dto.LoginRequest;
import com.hmap.backend.auth.dto.RegisterRequest;
import com.hmap.backend.auth.dto.ResetPasswordRequest;
import com.hmap.backend.auth.dto.TokenDTO;
import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.auth.entity.PasswordResetToken;
import com.hmap.backend.auth.repository.AuthRepository;
import com.hmap.backend.auth.repository.PasswordResetTokenRepository;
import com.hmap.backend.exception.BadRequestException;
import com.hmap.backend.notification.MailService;
import com.hmap.backend.role.enums.RoleName;
import com.hmap.backend.role.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lógica de autenticación: registro, inicio de sesión y recuperación de
 * contraseña (HU-003 a HU-006).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthRepository authRepository;
    private final RoleRepository roleRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final MailService mailService;

    @Value("${app.frontend.reset-password-url}")
    private String resetPasswordUrl;

    @Value("${app.security.reset-token.expiration-minutes}")
    private long resetTokenExpirationMinutes;

    public AuthService(AuthRepository authRepository,
                       RoleRepository roleRepository,
                       PasswordResetTokenRepository resetTokenRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       TokenService tokenService,
                       MailService mailService) {
        this.authRepository = authRepository;
        this.roleRepository = roleRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.mailService = mailService;
    }

    /** Registra una nueva cuenta de huésped (rol CLIENTE) y devuelve su token. */
    @Transactional
    public TokenDTO register(RegisterRequest request) {
        if (authRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Ya existe una cuenta con ese correo");
        }

        var clienteRole = roleRepository.findByName(RoleName.CLIENTE.name())
                .orElseThrow(() -> new IllegalStateException("Rol CLIENTE no encontrado."));

        var user = Auth.builder()
                .name(request.name())
                .lastName(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(clienteRole)
                .active(true)
                .build();

        authRepository.save(user);

        return new TokenDTO(tokenService.generateToken(user));
    }

    /** Autentica al usuario por correo y contraseña, y devuelve su token. */
    public TokenDTO login(LoginRequest request) {
        var authToken = new UsernamePasswordAuthenticationToken(request.email(), request.password());
        var authentication = authenticationManager.authenticate(authToken);

        var user = (Auth) authentication.getPrincipal();
        return new TokenDTO(tokenService.generateToken(user));
    }

    /**
     * Genera un token de recuperación y envía el enlace por correo (HU-005).
     * No revela si el correo existe para evitar enumeración de usuarios.
     */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        authRepository.findByEmail(request.email()).ifPresent(user -> {
            var resetToken = PasswordResetToken.builder()
                    .token(UUID.randomUUID().toString())
                    .user(user)
                    .expiresAt(LocalDateTime.now().plusMinutes(resetTokenExpirationMinutes))
                    .used(false)
                    .build();

            resetTokenRepository.save(resetToken);

            var link = resetPasswordUrl + "?token=" + resetToken.getToken();
            mailService.sendPasswordResetEmail(user.getEmail(), link);
            log.info("Enlace de recuperación enviado a {}", user.getEmail());
        });
    }

    /** Restablece la contraseña a partir de un token válido (HU-006). */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        var resetToken = resetTokenRepository.findByToken(request.token())
                .orElseThrow(() -> new BadRequestException("Token inválido"));

        if (!resetToken.isValid()) {
            throw new BadRequestException("El token ha expirado o ya fue utilizado");
        }

        var user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        authRepository.save(user);

        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);
    }

    /** Cambia la contraseña del usuario autenticado validando la actual (HU-015). */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        var user = authRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("La contraseña actual no es correcta");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        authRepository.save(user);
    }
}
