package com.hmap.backend.reception.service;

import com.hmap.backend.reception.dto.OccupancyDTO;
import com.hmap.backend.room.enums.RoomStatus;
import com.hmap.backend.room.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Métricas operativas del panel de recepción (HU-016). Calcula la ocupación
 * con una única consulta agrupada por estado (RNF-004).
 */
@Service
public class ReceptionService {

    private final RoomRepository roomRepository;

    public ReceptionService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /** Resumen de habitaciones ocupadas, libres, en mantenimiento y total. */
    @Transactional(readOnly = true)
    public OccupancyDTO getOccupancy() {
        long occupied = 0;
        long available = 0;
        long maintenance = 0;

        for (Object[] row : roomRepository.countGroupedByStatus()) {
            var status = (RoomStatus) row[0];
            long count = (long) row[1];
            switch (status) {
                case OCUPADA -> occupied = count;
                case DISPONIBLE -> available = count;
                case MANTENIMIENTO -> maintenance = count;
            }
        }

        return new OccupancyDTO(occupied, available, maintenance, occupied + available + maintenance);
    }
}
