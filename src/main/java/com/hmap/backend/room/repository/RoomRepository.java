package com.hmap.backend.room.repository;

import com.hmap.backend.reservation.enums.ReservationStatus;
import com.hmap.backend.room.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findBySlug(String slug);

    /**
     * Carga la habitación con bloqueo pesimista (SELECT ... FOR UPDATE).
     * Serializa la creación/edición de reservas por habitación para evitar
     * dobles reservas en condiciones de carrera.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findWithLockById(@Param("id") Long id);

    /**
     * Habitaciones libres en el rango [checkIn, checkOut) con capacidad suficiente.
     * Solape: una reserva activa bloquea si {@code checkIn < :checkOut && checkOut > :checkIn}.
     * Las habitaciones en MANTENIMIENTO se excluyen.
     */
    @Query("""
            select r from Room r
            where r.status <> com.hmap.backend.room.enums.RoomStatus.MANTENIMIENTO
              and r.capacity >= :guests
              and not exists (
                  select 1 from Reservation res
                  where res.room = r
                    and res.status in :activeStatuses
                    and res.checkIn < :checkOut
                    and res.checkOut > :checkIn)
            order by r.pricePerNight
            """)
    List<Room> findAvailable(@Param("checkIn") LocalDate checkIn,
                             @Param("checkOut") LocalDate checkOut,
                             @Param("guests") int guests,
                             @Param("activeStatuses") Collection<ReservationStatus> activeStatuses);
}
