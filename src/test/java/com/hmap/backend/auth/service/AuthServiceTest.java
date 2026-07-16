package com.hmap.backend.auth.service;

import com.hmap.backend.auth.dto.ForgotPasswordRequest;
import com.hmap.backend.auth.dto.LoginRequest;
import com.hmap.backend.auth.dto.RegisterRequest;
import com.hmap.backend.auth.dto.ResetPasswordRequest;
import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.auth.entity.PasswordResetToken;
import com.hmap.backend.auth.repository.AuthRepository;
import com.hmap.backend.auth.repository.PasswordResetTokenRepository;
import com.hmap.backend.exception.BadRequestException;
import com.hmap.backend.notification.MailService;
import com.hmap.backend.role.entity.Role;
import com.hmap.backend.role.enums.RoleName;
import com.hmap.backend.role.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthRepository authRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenService tokenService;
    @Mock private MailService mailService;

    @InjectMocks private AuthService authService;

    private Role clienteRole;

    @BeforeEach
    void setUp() {
        clienteRole = new Role(2L, RoleName.CLIENTE.name());
        ReflectionTestUtils.setField(authService, "resetPasswordUrl", "http://localhost:5173/reset-password");
        ReflectionTestUtils.setField(authService, "resetTokenExpirationMinutes", 30L);
    }

    @Test
    void register_creaUsuarioClienteYDevuelveToken() {
        var request = new RegisterRequest("Ana", "Pérez", "ana@mail.com", "claveSegura1");
        when(authRepository.existsByEmail("ana@mail.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.CLIENTE.name())).thenReturn(Optional.of(clienteRole));
        when(passwordEncoder.encode("claveSegura1")).thenReturn("hashed");
        when(tokenService.generateToken(any(Auth.class))).thenReturn("jwt-token");

        var result = authService.register(request);

        assertThat(result.token()).isEqualTo("jwt-token");

        var saved = ArgumentCaptor.forClass(Auth.class);
        verify(authRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Ana");
        assertThat(saved.getValue().getLastName()).isEqualTo("Pérez");
        assertThat(saved.getValue().getEmail()).isEqualTo("ana@mail.com");
        assertThat(saved.getValue().getPassword()).isEqualTo("hashed");
        assertThat(saved.getValue().getRole().getName()).isEqualTo(RoleName.CLIENTE.name());
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    void register_correoExistente_lanzaBadRequest() {
        var request = new RegisterRequest("Ana", "Pérez", "ana@mail.com", "claveSegura1");
        when(authRepository.existsByEmail("ana@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class);

        verify(authRepository, never()).save(any());
    }

    @Test
    void login_credencialesValidas_devuelveToken() {
        var request = new LoginRequest("ana@mail.com", "claveSegura1");
        var user = Auth.builder().email("ana@mail.com").role(clienteRole).active(true).build();
        var authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(tokenService.generateToken(user)).thenReturn("jwt-token");

        var result = authService.login(request);

        assertThat(result.token()).isEqualTo("jwt-token");
    }

    @Test
    void login_credencialesInvalidas_propagaExcepcion() {
        var request = new LoginRequest("ana@mail.com", "mala");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void requestPasswordReset_usuarioExiste_guardaTokenYEnviaCorreo() {
        var user = Auth.builder().email("ana@mail.com").role(clienteRole).active(true).build();
        when(authRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(user));

        authService.requestPasswordReset(new ForgotPasswordRequest("ana@mail.com"));

        verify(resetTokenRepository).save(any(PasswordResetToken.class));
        verify(mailService).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void requestPasswordReset_usuarioNoExiste_noHaceNada() {
        when(authRepository.findByEmail("nadie@mail.com")).thenReturn(Optional.empty());

        authService.requestPasswordReset(new ForgotPasswordRequest("nadie@mail.com"));

        verify(resetTokenRepository, never()).save(any());
        verify(mailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void resetPassword_tokenValido_actualizaPasswordYMarcaUsado() {
        var user = Auth.builder().email("ana@mail.com").password("old").role(clienteRole).active(true).build();
        var token = PasswordResetToken.builder()
                .token("valid-token")
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .build();
        when(resetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("nuevaClave1")).thenReturn("new-hash");

        authService.resetPassword(new ResetPasswordRequest("valid-token", "nuevaClave1"));

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(token.isUsed()).isTrue();
        verify(authRepository).save(user);
        verify(resetTokenRepository).save(token);
    }

    @Test
    void resetPassword_tokenInexistente_lanzaBadRequest() {
        when(resetTokenRepository.findByToken("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("x", "nuevaClave1")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void resetPassword_tokenExpirado_lanzaBadRequest() {
        var token = PasswordResetToken.builder()
                .token("expired")
                .user(Auth.builder().build())
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .used(false)
                .build();
        when(resetTokenRepository.findByToken("expired")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("expired", "nuevaClave1")))
                .isInstanceOf(BadRequestException.class);

        verify(authRepository, never()).save(any());
    }
}
