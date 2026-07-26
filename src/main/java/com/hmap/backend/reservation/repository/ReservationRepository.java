package com.hmap.backend.reservation.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hmap.backend.reservation.entity.Reservation;
import com.hmap.backend.reservation.enums.ReservationStatus;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Reservas del usuario, más recientes primero, con habitación y huésped cargados. */
    @EntityGraph(attributePaths = {"room", "user"})
    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Reserva con su habitación y huésped (evita N+1 al mapear el DTO). */
    @EntityGraph(attributePaths = {"room", "user"})
    Optional<Reservation> findWithRoomById(Long id);

    /**
     * ¿Existe una reserva activa que solape el rango [checkIn, checkOut)?
     * {@code excludeId} permite excluir la propia reserva al editarla (null al crear).
     */
    @Query("""
            select count(r) > 0 from Reservation r
            where r.room.id = :roomId
              and r.status in :activeStatuses
              and r.checkIn < :checkOut
              and r.checkOut > :checkIn
              and (:excludeId is null or r.id <> :excludeId)
            """)
    boolean existsOverlapping(@Param("roomId") Long roomId,
                              @Param("checkIn") LocalDate checkIn,
                              @Param("checkOut") LocalDate checkOut,
                              @Param("activeStatuses") Collection<ReservationStatus> activeStatuses,
                              @Param("excludeId") Long excludeId);

    /** ¿La habitación tiene reservas que la bloquean? (regla de no eliminar, HU-027). */
    boolean existsByRoomIdAndStatusIn(Long roomId, Collection<ReservationStatus> statuses);

    // === Panel de recepción (E3) ===

    /** Entradas programadas para una fecha (check-ins del día, HU-018). */
    @EntityGraph(attributePaths = {"room", "user"})
    @Query("select r from Reservation r where r.checkIn = :checkIn and r.status in :statuses order by r.checkIn asc")
    List<Reservation> findByCheckInAndStatusInOrderByCheckInAsc(
            @Param("checkIn") LocalDate checkIn,
            @Param("statuses") Collection<ReservationStatus> statuses);

    /** Salidas programadas para una fecha (check-outs del día, HU-018). */
    @EntityGraph(attributePaths = {"room", "user"})
    @Query("select r from Reservation r where r.checkOut = :checkOut and r.status in :statuses order by r.checkOut asc")
    List<Reservation> findByCheckOutAndStatusInOrderByCheckOutAsc(
            @Param("checkOut") LocalDate checkOut,
            @Param("statuses") Collection<ReservationStatus> statuses);

    /** Reservas que tocan el rango del calendario (solape con [from, to], HU-017). */
    @EntityGraph(attributePaths = {"room", "user"})
    @Query("""
            select r from Reservation r
            where r.status <> com.hmap.backend.reservation.enums.ReservationStatus.CANCELADA
              and r.checkIn <= :to
              and r.checkOut >= :from
            order by r.checkIn
            """)
    List<Reservation> findForCalendar(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Tabla global paginada con filtros opcionales (HU-023/024). Cualquier filtro
     * nulo se ignora. {@code search} busca por nombre, apellido o correo del
     * huésped; {@code searchId} (derivado en el servicio) permite buscar por id/código.
     */
    @EntityGraph(attributePaths = {"room", "user"})
    @Query("""
            select r from Reservation r
            where (:status is null or r.status = :status)
              and (:from is null or r.checkIn >= :from)
              and (:to is null or r.checkOut <= :to)
              and (:search is null
                   or lower(r.user.name) like lower(concat('%', :search, '%'))
                   or lower(r.user.lastName) like lower(concat('%', :search, '%'))
                   or lower(r.user.email) like lower(concat('%', :search, '%'))
                   or (:searchId is not null and r.id = :searchId))
            """)
    Page<Reservation> search(@Param("search") String search,
                             @Param("searchId") Long searchId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("status") ReservationStatus status,
                             Pageable pageable);
}
