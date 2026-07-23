package com.hmap.backend.room.service;

import com.hmap.backend.exception.BadRequestException;
import com.hmap.backend.exception.ResourceNotFoundException;
import com.hmap.backend.room.entity.Room;
import com.hmap.backend.room.enums.RoomStatus;
import com.hmap.backend.room.repository.RoomRepository;
import com.hmap.backend.room.support.ImageUrlResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private RoomRepository roomRepository;

    // Real con base vacía: resolve() devuelve la ruta tal cual
    @Spy private ImageUrlResolver imageUrlResolver = new ImageUrlResolver("");

    @InjectMocks private RoomService roomService;

    private Room buildRoom() {
        return Room.builder()
                .id(1L)
                .slug("deluxe-cama-grande")
                .name("Habitación Deluxe con cama extragrande")
                .description("Una cama extragrande...")
                .capacity(2)
                .area(20)
                .bedsLabel("1 cama doble grande")
                .pricePerNight(new BigDecimal("240.00"))
                .smokingPolicy("No se puede fumar")
                .status(RoomStatus.DISPONIBLE)
                .images(List.of("https://img/1.jpg"))
                .amenities(List.of("Aire acondicionado"))
                .bathroom(List.of("Bañera"))
                .views(List.of("Vistas al jardín"))
                .build();
    }

    @Test
    void findAll_mapeaCatalogoADto() {
        when(roomRepository.findAll()).thenReturn(List.of(buildRoom()));

        var result = roomService.findAll();

        assertThat(result).hasSize(1);
        var dto = result.get(0);
        assertThat(dto.slug()).isEqualTo("deluxe-cama-grande");
        assertThat(dto.pricePerNight()).isEqualByComparingTo("240.00");
        assertThat(dto.status()).isEqualTo("DISPONIBLE");
        assertThat(dto.images()).containsExactly("https://img/1.jpg");
    }

    @Test
    void findById_inexistente_lanzaNotFound() {
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Habitación no encontrada");
    }

    @Test
    void findAvailable_delegaAlRepositorioConEstadosActivos() {
        var checkIn = LocalDate.now().plusDays(7);
        var checkOut = LocalDate.now().plusDays(10);
        when(roomRepository.findAvailable(checkIn, checkOut, 2, RoomService.ACTIVE_STATUSES))
                .thenReturn(List.of(buildRoom()));

        var result = roomService.findAvailable(checkIn, checkOut, 2);

        assertThat(result).hasSize(1);
        verify(roomRepository).findAvailable(checkIn, checkOut, 2, RoomService.ACTIVE_STATUSES);
    }

    @Test
    void findAvailable_checkInPasado_lanzaBadRequest() {
        assertThatThrownBy(() -> roomService.findAvailable(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(2), 2))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La fecha de entrada no puede ser anterior a hoy");

        verify(roomRepository, never()).findAvailable(any(), any(), anyInt(), any());
    }

    @Test
    void findAvailable_checkOutNoPosterior_lanzaBadRequest() {
        var fecha = LocalDate.now().plusDays(5);

        assertThatThrownBy(() -> roomService.findAvailable(fecha, fecha, 2))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La fecha de salida debe ser posterior a la de entrada");
    }

    @Test
    void findAvailable_huespedesMenorAUno_lanzaBadRequest() {
        assertThatThrownBy(() -> roomService.findAvailable(
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La cantidad de huéspedes debe ser al menos 1");
    }
}
