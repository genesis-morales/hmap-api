package com.hmap.backend.user.service;

import com.hmap.backend.auth.dto.UserDTO;
import com.hmap.backend.auth.repository.AuthRepository;
import com.hmap.backend.exception.ResourceNotFoundException;
import com.hmap.backend.user.dto.UpdateProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestión del perfil del usuario autenticado (HU-014). */
@Service
public class UserService {

    private final AuthRepository authRepository;

    public UserService(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    /** Actualiza los datos de contacto del usuario y devuelve el perfil resultante. */
    @Transactional
    public UserDTO updateProfile(Long userId, UpdateProfileRequest request) {
        var user = authRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        user.setName(request.name());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        authRepository.save(user);

        return UserDTO.from(user);
    }
}
