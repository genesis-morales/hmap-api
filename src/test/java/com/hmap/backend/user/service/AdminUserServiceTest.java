package com.hmap.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.auth.repository.AuthRepository;
import com.hmap.backend.exception.BadRequestException;
import com.hmap.backend.exception.ConflictException;
import com.hmap.backend.exception.ResourceNotFoundException;
import com.hmap.backend.role.entity.Role;
import com.hmap.backend.role.enums.RoleName;
import com.hmap.backend.role.repository.RoleRepository;
import com.hmap.backend.user.dto.CreateUserRequest;
import com.hmap.backend.user.dto.UpdateUserRequest;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private AuthRepository authRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks private AdminUserService adminUserService;

    private Role role(RoleName name, long id) {
        return new Role(id, name.name());
    }

    private Auth user(long id, RoleName roleName, boolean active) {
        return Auth.builder()
                .id(id)
                .name("Ana")
                .lastName("Pérez")
                .email("ana@hmap.com")
                .role(role(roleName, roleName.ordinal() + 1L))
                .active(active)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // === create ===

    @Test
    void create_conRolAsignableYCorreoLibre_persisteYDevuelveDto() {
        var request = new CreateUserRequest("Ana", "Pérez", "ana@hmap.com", "88880000",
                "secret123", RoleName.RECEPCIONISTA);
        when(roleRepository.findByName("RECEPCIONISTA"))
                .thenReturn(Optional.of(role(RoleName.RECEPCIONISTA, 3L)));
        when(authRepository.existsByEmail("ana@hmap.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("ENCODED");

        var result = adminUserService.create(request);

        assertThat(result.email()).isEqualTo("ana@hmap.com");
        assertThat(result.role()).isEqualTo("RECEPCIONISTA");
        assertThat(result.active()).isTrue();
        verify(authRepository).save(any(Auth.class));
    }

    @Test
    void create_conCorreoDuplicado_lanzaConflict() {
        var request = new CreateUserRequest("Ana", "Pérez", "ana@hmap.com", null,
                "secret123", RoleName.ADMINISTRADOR);
        when(roleRepository.findByName("ADMINISTRADOR"))
                .thenReturn(Optional.of(role(RoleName.ADMINISTRADOR, 1L)));
        when(authRepository.existsByEmail("ana@hmap.com")).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.create(request))
                .isInstanceOf(ConflictException.class);
        verify(authRepository, never()).save(any());
    }

    @Test
    void create_conRolNoAsignable_lanzaBadRequest() {
        var request = new CreateUserRequest("Ana", "Pérez", "ana@hmap.com", null,
                "secret123", RoleName.CLIENTE);

        assertThatThrownBy(() -> adminUserService.create(request))
                .isInstanceOf(BadRequestException.class);
        verify(authRepository, never()).save(any());
    }

    // === update ===

    @Test
    void update_adminSeQuitaSuPropioRol_lanzaConflict() {
        var admin = user(1L, RoleName.ADMINISTRADOR, true);
        when(authRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(roleRepository.findByName("RECEPCIONISTA"))
                .thenReturn(Optional.of(role(RoleName.RECEPCIONISTA, 3L)));
        var request = new UpdateUserRequest("Ana", "Pérez", null, RoleName.RECEPCIONISTA);

        assertThatThrownBy(() -> adminUserService.update(1L, 1L, request))
                .isInstanceOf(ConflictException.class);
        verify(authRepository, never()).save(any());
    }

    @Test
    void update_aOtroUsuario_cambiaDatosYRol() {
        var target = user(2L, RoleName.RECEPCIONISTA, true);
        when(authRepository.findById(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findByName("ADMINISTRADOR"))
                .thenReturn(Optional.of(role(RoleName.ADMINISTRADOR, 1L)));
        var request = new UpdateUserRequest("Ana María", "Pérez", "70000000", RoleName.ADMINISTRADOR);

        var result = adminUserService.update(1L, 2L, request);

        assertThat(result.name()).isEqualTo("Ana María");
        assertThat(result.role()).isEqualTo("ADMINISTRADOR");
        verify(authRepository).save(target);
    }

    @Test
    void update_usuarioInexistente_lanzaNotFound() {
        when(authRepository.findById(99L)).thenReturn(Optional.empty());
        var request = new UpdateUserRequest("Ana", "Pérez", null, RoleName.RECEPCIONISTA);

        assertThatThrownBy(() -> adminUserService.update(1L, 99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // === setActive ===

    @Test
    void setActive_adminSeDesactivaASiMismo_lanzaConflict() {
        var admin = user(1L, RoleName.ADMINISTRADOR, true);
        when(authRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> adminUserService.setActive(1L, 1L, false))
                .isInstanceOf(ConflictException.class);
        verify(authRepository, never()).save(any());
    }

    @Test
    void setActive_suspendeAOtroUsuario_actualizaEstado() {
        var target = user(2L, RoleName.RECEPCIONISTA, true);
        when(authRepository.findById(2L)).thenReturn(Optional.of(target));

        var result = adminUserService.setActive(1L, 2L, false);

        assertThat(result.active()).isFalse();
        verify(authRepository).save(target);
    }

    @Test
    void setActive_reactivaLaPropiaCuenta_esPermitido() {
        var admin = user(1L, RoleName.ADMINISTRADOR, false);
        when(authRepository.findById(1L)).thenReturn(Optional.of(admin));

        var result = adminUserService.setActive(1L, 1L, true);

        assertThat(result.active()).isTrue();
        verify(authRepository).save(admin);
    }
}
