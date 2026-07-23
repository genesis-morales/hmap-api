package com.hmap.backend.reservation.repository;

import com.hmap.backend.reservation.entity.Reservation;
import com.hmap.backend.reservation.enums.ReservationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Reservas del usuario, más recientes primero, con la habitación ya cargada. */
    @EntityGraph(attributePaths = {"room"})
    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Reserva con su habitación (evita N+1 al mapear el DTO). */
    @EntityGraph(attributePaths = {"room"})
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
}
