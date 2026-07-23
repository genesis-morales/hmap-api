package com.hmap.backend.user.service;

import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.auth.repository.AuthRepository;
import com.hmap.backend.exception.ResourceNotFoundException;
import com.hmap.backend.role.entity.Role;
import com.hmap.backend.role.enums.RoleName;
import com.hmap.backend.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private AuthRepository authRepository;

    @InjectMocks private UserService userService;

    private Auth user;

    @BeforeEach
    void setUp() {
        user = Auth.builder()
                .id(1L)
                .name("Ana")
                .lastName("Pérez")
                .email("ana@mail.com")
                .role(new Role(2L, RoleName.CLIENTE.name()))
                .active(true)
                .build();
    }

    @Test
    void updateProfile_actualizaNombreApellidoYTelefono() {
        when(authRepository.findById(1L)).thenReturn(Optional.of(user));

        var result = userService.updateProfile(1L, new UpdateProfileRequest("Ana María", "Pérez Mora", "+506 8888-8888"));

        assertThat(result.name()).isEqualTo("Ana María");
        assertThat(result.lastName()).isEqualTo("Pérez Mora");
        assertThat(result.phone()).isEqualTo("+506 8888-8888");
        assertThat(result.email()).isEqualTo("ana@mail.com");
        verify(authRepository).save(user);
    }

    @Test
    void updateProfile_telefonoNull_esValido() {
        when(authRepository.findById(1L)).thenReturn(Optional.of(user));

        var result = userService.updateProfile(1L, new UpdateProfileRequest("Ana", "Pérez", null));

        assertThat(result.phone()).isNull();
        verify(authRepository).save(user);
    }

    @Test
    void updateProfile_usuarioInexistente_lanzaNotFound() {
        when(authRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(99L, new UpdateProfileRequest("Ana", "Pérez", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
