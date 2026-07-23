package com.hmap.backend.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hmap.backend.room.enums.RoomStatus;
import com.hmap.backend.room.repository.RoomRepository;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private RoomRepository roomRepository;

    @InjectMocks private DashboardService dashboardService;

    @Test
    void getOccupancy_agrupaConteosPorEstado() {
        when(roomRepository.countGroupedByStatus()).thenReturn(List.of(
                new Object[]{RoomStatus.DISPONIBLE, 5L},
                new Object[]{RoomStatus.OCUPADA, 3L},
                new Object[]{RoomStatus.MANTENIMIENTO, 1L}));

        var result = dashboardService.getOccupancy();

        assertThat(result.available()).isEqualTo(5);
        assertThat(result.occupied()).isEqualTo(3);
        assertThat(result.maintenance()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(9);
    }

    @Test
    void getOccupancy_sinHabitaciones_devuelveCeros() {
        when(roomRepository.countGroupedByStatus()).thenReturn(List.of());

        var result = dashboardService.getOccupancy();

        assertThat(result.total()).isZero();
    }
}
