package com.hmap.backend.auth.repository;

import com.hmap.backend.auth.entity.Auth;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthRepository extends JpaRepository<Auth, Long> {

    Optional<Auth> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Nómina paginada con filtros opcionales para el panel admin (HU-034).
     * Cualquier filtro nulo se ignora. {@code role} filtra por el nombre del rol
     * (ADMINISTRADOR/RECEPCIONISTA/CLIENTE); {@code search} busca por nombre,
     * apellido o correo.
     */
    @Query("""
            select a from Auth a
            where (:role is null or a.role.name = :role)
              and (:active is null or a.active = :active)
              and (:search is null
                   or lower(a.name) like lower(concat('%', :search, '%'))
                   or lower(a.lastName) like lower(concat('%', :search, '%'))
                   or lower(a.email) like lower(concat('%', :search, '%')))
            """)
    Page<Auth> searchUsers(@Param("role") String role,
                           @Param("active") Boolean active,
                           @Param("search") String search,
                           Pageable pageable);
}
