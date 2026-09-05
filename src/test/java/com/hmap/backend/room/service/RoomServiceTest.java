package com.hmap.backend.room.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hmap.backend.exception.BadRequestException;
import com.hmap.backend.exception.ConflictException;
import com.hmap.backend.exception.ResourceNotFoundException;
import com.hmap.backend.room.dto.RoomRequest;
import com.hmap.backend.room.dto.RoomStatusRequest;
import com.hmap.backend.room.entity.Room;
import com.hmap.backend.room.enums.RoomStatus;
import com.hmap.backend.room.repository.RoomRepository;
import com.hmap.backend.room.support.ImageUrlResolver;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private com.hmap.backend.reservation.repository.ReservationRepository reservationRepository;

    // Real con base vacía: resolve() devuelve la ruta tal cual
    @Spy private ImageUrlResolver imageUrlResolver = new ImageUrlResolver("");

    @InjectMocks private RoomService roomService;

    private Room buildRoom() {
        return Room.builder()
                .id(1L)
                .slug("deluxe-cama-grande")
                .roomNumber("102")
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
        when(roomRepository.findAvailable(checkIn, checkOut, 2, RoomService.BLOCKING_STATUSES))
                .thenReturn(List.of(buildRoom()));

        var result = roomService.findAvailable(checkIn, checkOut, 2);

        assertThat(result).hasSize(1);
        verify(roomRepository).findAvailable(checkIn, checkOut, 2, RoomService.BLOCKING_STATUSES);
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

    // === inventario (HU-025 a HU-028) ===

    private RoomRequest buildRoomRequest(String slug, String status) {
        return new RoomRequest(slug, "201", "Nueva habitación", "Descripción", 2, 15, "1 cama doble",
                new BigDecimal("100.00"), "No se puede fumar", status,
                List.of("hmap/rooms/nueva/bed"), List.of("Wifi"), List.of("Ducha"), List.of("Vistas al jardín"));
    }

    /** Room con colecciones mutables (como las entrega Hibernate al cargar). */
    private Room buildMutableRoom() {
        return Room.builder()
                .id(1L).slug("old-slug").roomNumber("101").name("Antigua").description("desc")
                .capacity(2).area(10).bedsLabel("1 cama").pricePerNight(new BigDecimal("90.00"))
                .smokingPolicy("No se puede fumar").status(RoomStatus.DISPONIBLE)
                .build();
    }

    @Test
    void create_habitacionValida_seCreaComoDisponible() {
        var request = buildRoomRequest("nueva-hab", null);
        when(roomRepository.existsBySlug("nueva-hab")).thenReturn(false);

        var result = roomService.create(request);

        assertThat(result.slug()).isEqualTo("nueva-hab");
        assertThat(result.status()).isEqualTo("DISPONIBLE");
        assertThat(result.amenities()).containsExactly("Wifi");
        verify(roomRepository).save(any(Room.class));
    }

    @Test
    void create_slugDuplicado_lanzaConflict() {
        var request = buildRoomRequest("nueva-hab", null);
        when(roomRepository.existsBySlug("nueva-hab")).thenReturn(true);

        assertThatThrownBy(() -> roomService.create(request))
                .isInstanceOf(ConflictException.class)
                // El campo permite al FE anclar el error al input del slug.
                .extracting(e -> ((ConflictException) e).getField()).isEqualTo("slug");
        verify(roomRepository, never()).save(any());
    }

    @Test
    void create_numeroDeHabitacionDuplicado_lanzaConflict() {
        var request = buildRoomRequest("nueva-hab", null);
        when(roomRepository.existsBySlug("nueva-hab")).thenReturn(false);
        when(roomRepository.existsByRoomNumber("201")).thenReturn(true);

        assertThatThrownBy(() -> roomService.create(request))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getField()).isEqualTo("room_number");
        verify(roomRepository, never()).save(any());
    }

    @Test
    void update_habitacionExistente_actualizaCampos() {
        var existing = buildMutableRoom();
        var request = buildRoomRequest("nueva-hab", null);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roomRepository.existsBySlugAndIdNot("nueva-hab", 1L)).thenReturn(false);

        var result = roomService.update(1L, request);

        assertThat(result.name()).isEqualTo("Nueva habitación");
        assertThat(result.slug()).isEqualTo("nueva-hab");
        assertThat(result.roomNumber()).isEqualTo("201");
        verify(roomRepository).save(existing);
    }

    @Test
    void update_slugDuplicadoDeOtra_lanzaConflict() {
        var existing = buildMutableRoom();
        var request = buildRoomRequest("nueva-hab", null);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roomRepository.existsBySlugAndIdNot("nueva-hab", 1L)).thenReturn(true);

        assertThatThrownBy(() -> roomService.update(1L, request))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getField()).isEqualTo("slug");
    }

    @Test
    void delete_conReservasActivas_lanzaConflict() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(buildRoom()));
        when(reservationRepository.existsByRoomIdAndStatusIn(1L, RoomService.BLOCKING_STATUSES))
                .thenReturn(true);

        assertThatThrownBy(() -> roomService.delete(1L))
                .isInstanceOf(ConflictException.class);
        verify(roomRepository, never()).delete(any());
    }

    @Test
    void delete_sinReservasActivas_elimina() {
        var room = buildRoom();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(reservationRepository.existsByRoomIdAndStatusIn(1L, RoomService.BLOCKING_STATUSES))
                .thenReturn(false);

        roomService.delete(1L);

        verify(roomRepository).delete(room);
    }

    @Test
    void changeStatus_valido_actualizaEstado() {
        var room = buildRoom();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        var result = roomService.changeStatus(1L, new RoomStatusRequest("MANTENIMIENTO"));

        assertThat(room.getStatus()).isEqualTo(RoomStatus.MANTENIMIENTO);
        assertThat(result.status()).isEqualTo("MANTENIMIENTO");
    }

    @Test
    void changeStatus_invalido_lanzaBadRequest() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(buildRoom()));

        assertThatThrownBy(() -> roomService.changeStatus(1L, new RoomStatusRequest("VOLANDO")))
                .isInstanceOf(BadRequestException.class);
    }
}
