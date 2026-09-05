package com.hmap.backend.user.service;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.auth.repository.AuthRepository;
import com.hmap.backend.common.dto.PageRequests;
import com.hmap.backend.common.dto.PageResponse;
import com.hmap.backend.exception.BadRequestException;
import com.hmap.backend.exception.ConflictException;
import com.hmap.backend.exception.ResourceNotFoundException;
import com.hmap.backend.role.entity.Role;
import com.hmap.backend.role.enums.RoleName;
import com.hmap.backend.role.repository.RoleRepository;
import com.hmap.backend.user.dto.AdminUserDTO;
import com.hmap.backend.user.dto.CreateUserRequest;
import com.hmap.backend.user.dto.UpdateUserRequest;

/**
 * Gestión de cuentas de personal interno desde el panel de administrador
 * (HU-030 a HU-034). Todas las operaciones las ejecuta un ADMINISTRADOR; la
 * autorización por rol se aplica en el controller con {@code @PreAuthorize}.
 */
@Service
public class AdminUserService {

    /** Roles que un administrador puede asignar a una cuenta interna. */
    private static final Set<RoleName> ASSIGNABLE_ROLES =
            EnumSet.of(RoleName.RECEPCIONISTA, RoleName.ADMINISTRADOR);

    private final AuthRepository authRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(AuthRepository authRepository,
                            RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder) {
        this.authRepository = authRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Nómina paginada con filtros opcionales por rol, estado y texto (HU-034). */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserDTO> list(RoleName role, Boolean active, String search, int page, int size) {
        var normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        var pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

        var result = authRepository.searchUsers(
                role == null ? null : role.name(), active, normalizedSearch, pageable);

        return PageResponse.from(result, AdminUserDTO::from);
    }

    /** Crea una cuenta interna con rol asignado y contraseña inicial (HU-030/032). */
    @Transactional
    public AdminUserDTO create(CreateUserRequest request) {
        var role = resolveAssignableRole(request.role());

        if (authRepository.existsByEmail(request.email())) {
            throw new ConflictException("Ya existe una cuenta con ese correo", "email");
        }

        var user = Auth.builder()
                .name(request.name())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .password(passwordEncoder.encode(request.password()))
                .role(role)
                .active(true)
                .build();

        authRepository.save(user);
        return AdminUserDTO.from(user);
    }

    /**
     * Edita los datos y el rol de una cuenta (HU-031/032). Un administrador no
     * puede quitarse su propio rol de administrador para no perder el acceso.
     */
    @Transactional
    public AdminUserDTO update(Long adminId, Long id, UpdateUserRequest request) {
        var user = authRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        var newRole = resolveAssignableRole(request.role());

        if (user.getId().equals(adminId) && !RoleName.ADMINISTRADOR.name().equals(newRole.getName())) {
            throw new ConflictException("No puedes quitarte tu propio rol de administrador");
        }

        user.setName(request.name());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setRole(newRole);

        authRepository.save(user);
        return AdminUserDTO.from(user);
    }

    /**
     * Activa o suspende una cuenta (HU-033). Un administrador no puede
     * desactivar su propia cuenta para no quedarse sin acceso.
     *
     * <p>Al desactivar, la observación es obligatoria: el administrador debe
     * justificar la medida. Al reactivar se limpia, porque describe una
     * suspensión que ya no está vigente.
     */
    @Transactional
    public AdminUserDTO setActive(Long adminId, Long id, boolean active, String observation) {
        var user = authRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (user.getId().equals(adminId) && !active) {
            throw new ConflictException("No puedes desactivar tu propia cuenta");
        }

        if (active) {
            user.setDeactivationReason(null);
        } else {
            if (observation == null || observation.isBlank()) {
                throw new BadRequestException(
                        "Debes indicar el motivo de la desactivación", "observation");
            }
            user.setDeactivationReason(observation.trim());
        }

        user.setActive(active);
        authRepository.save(user);
        return AdminUserDTO.from(user);
    }

    /** Valida que el rol sea asignable y devuelve la entidad Role correspondiente. */
    private Role resolveAssignableRole(RoleName roleName) {
        if (roleName == null || !ASSIGNABLE_ROLES.contains(roleName)) {
            throw new BadRequestException("El rol debe ser RECEPCIONISTA o ADMINISTRADOR", "role");
        }
        return roleRepository.findByName(roleName.name())
                .orElseThrow(() -> new IllegalStateException("Rol " + roleName + " no encontrado."));
    }
}
