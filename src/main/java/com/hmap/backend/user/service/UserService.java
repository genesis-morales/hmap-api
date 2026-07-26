package com.hmap.backend.user.service;

import java.security.SecureRandom;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hmap.backend.auth.dto.UserDTO;
import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.auth.repository.AuthRepository;
import com.hmap.backend.exception.ResourceNotFoundException;
import com.hmap.backend.role.enums.RoleName;
import com.hmap.backend.role.repository.RoleRepository;
import com.hmap.backend.user.dto.UpdateProfileRequest;

/** Gestión del perfil del usuario autenticado (HU-014) y provisión de cuentas. */
@Service
public class UserService {

    /** Alfabeto sin caracteres ambiguos para las contraseñas temporales. */
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final AuthRepository authRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public UserService(AuthRepository authRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.authRepository = authRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
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

    /**
     * Devuelve la cuenta CLIENTE asociada al correo, creándola si no existe.
     * La usa la reserva manual (HU-020): un huésped atendido por teléfono o en
     * recepción puede no tener cuenta todavía. Cuando se crea, se genera una
     * contraseña temporal que el llamador envía por correo (HU-037).
     *
     * @return la cuenta y la contraseña temporal ({@code null} si ya existía).
     */
    @Transactional
    public ProvisionedGuest findOrCreateGuest(String name, String lastName, String email, String phone) {
        var existing = authRepository.findByEmail(email);
        if (existing.isPresent()) {
            return new ProvisionedGuest(existing.get(), null);
        }

        var clienteRole = roleRepository.findByName(RoleName.CLIENTE.name())
                .orElseThrow(() -> new IllegalStateException("Rol CLIENTE no encontrado."));

        var temporaryPassword = generateTemporaryPassword();
        var user = Auth.builder()
                .name(name)
                .lastName(lastName)
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(temporaryPassword))
                .role(clienteRole)
                .active(true)
                .build();

        authRepository.save(user);
        return new ProvisionedGuest(user, temporaryPassword);
    }

    private String generateTemporaryPassword() {
        var sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Cuenta de huésped resuelta, con la contraseña temporal si se acaba de crear. */
    public record ProvisionedGuest(Auth user, String temporaryPassword) {

        public boolean isNewAccount() {
            return temporaryPassword != null;
        }
    }
}
