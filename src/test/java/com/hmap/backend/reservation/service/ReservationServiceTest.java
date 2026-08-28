package com.hmap.backend.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.hmap.backend.auth.entity.Auth;
import com.hmap.backend.exception.BadRequestException;
import com.hmap.backend.exception.ConflictException;
import com.hmap.backend.exception.ForbiddenException;
import com.hmap.backend.exception.ResourceNotFoundException;
import com.hmap.backend.notification.MailService;
import com.hmap.backend.reservation.dto.CancelReservationRequest;
import com.hmap.backend.reservation.dto.CreateReservationRequest;
import com.hmap.backend.reservation.dto.ManualReservationRequest;
import com.hmap.backend.reservation.dto.UpdateReservationRequest;
import com.hmap.backend.reservation.entity.Reservation;
import com.hmap.backend.reservation.enums.ReservationStatus;
import com.hmap.backend.reservation.repository.ReservationRepository;
import com.hmap.backend.role.entity.Role;
import com.hmap.backend.role.enums.RoleName;
import com.hmap.backend.room.entity.Room;
import com.hmap.backend.room.enums.RoomStatus;
import com.hmap.backend.room.repository.RoomRepository;
import com.hmap.backend.room.service.RoomService;
import com.hmap.backend.room.support.ImageUrlResolver;
import com.hmap.backend.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private MailService mailService;
    @Mock private UserService userService;

    // Real con base vacía: resolve() devuelve la ruta tal cual
    @Spy private ImageUrlResolver imageUrlResolver = new ImageUrlResolver("");

    @InjectMocks private ReservationService reservationService;

    private Auth user;
    private Room room;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reservationService, "editWindowHours", 48L);

        user = Auth.builder()
                .id(1L)
                .name("Ana")
                .lastName("Pérez")
                .email("ana@mail.com")
                .role(new Role(2L, RoleName.CLIENTE.name()))
                .active(true)
                .build();

        room = Room.builder()
                .id(10L)
                .slug("deluxe-cama-grande")
                .name("Habitación Deluxe con cama extragrande")
                .description("desc")
                .capacity(2)
                .area(20)
                .bedsLabel("1 cama doble grande")
                .pricePerNight(new BigDecimal("240.00"))
                .smokingPolicy("No se puede fumar")
                .status(RoomStatus.DISPONIBLE)
                .build();
    }

    private Reservation buildReservation(LocalDate checkIn, LocalDate checkOut, ReservationStatus status) {
        return Reservation.builder()
                .id(123L)
                .user(user)
                .room(room)
                .checkIn(checkIn)
                .checkOut(checkOut)
                .guests(2)
                .total(new BigDecimal("720.00"))
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // === create ===

    @Test
    void create_reservaValida_nacePendienteConTotalCalculado() {
        var checkIn = LocalDate.now().plusDays(7);
        var checkOut = LocalDate.now().plusDays(10); // 3 noches
        var request = new CreateReservationRequest(10L, checkIn, checkOut, 2);
        when(roomRepository.findWithLockById(10L)).thenReturn(Optional.of(room));
        when(reservationRepository.existsOverlapping(10L, checkIn, checkOut, RoomService.BLOCKING_STATUSES, null))
                .thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(123L);
            return r;
        });

        var result = reservationService.create(user, request);

        var saved = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ReservationStatus.PENDIENTE);
        assertThat(saved.getValue().getTotal()).isEqualByComparingTo("720.00"); // 3 * 240

        assertThat(result.status()).isEqualTo("PENDIENTE");
        assertThat(result.nights()).isEqualTo(3);
        assertThat(result.code()).isEqualTo("RSV-000123");
        assertThat(result.canEdit()).isTrue();
        assertThat(result.canCancel()).isTrue();
    }

    @Test
    void create_conSolape_lanzaConflictYNoGuarda() {
        var checkIn = LocalDate.now().plusDays(7);
        var checkOut = LocalDate.now().plusDays(10);
        when(roomRepository.findWithLockById(10L)).thenReturn(Optional.of(room));
        when(reservationRepository.existsOverlapping(10L, checkIn, checkOut, RoomService.BLOCKING_STATUSES, null))
                .thenReturn(true);

        assertThatThrownBy(() -> reservationService.create(user,
                new CreateReservationRequest(10L, checkIn, checkOut, 2)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("La habitación ya no está disponible en esas fechas.")
                // El campo permite al FE anclar el error al selector de fechas.
                .extracting(e -> ((ConflictException) e).getField()).isEqualTo("check_in");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_huespedesSuperanCapacidad_lanzaBadRequest() {
        when(roomRepository.findWithLockById(10L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> reservationService.create(user,
                new CreateReservationRequest(10L, LocalDate.now().plusDays(7), LocalDate.now().plusDays(10), 5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La habitación admite hasta 2 huéspedes")
                .extracting(e -> ((BadRequestException) e).getField()).isEqualTo("guests");
    }

    @Test
    void create_habitacionEnMantenimiento_lanzaConflict() {
        room.setStatus(RoomStatus.MANTENIMIENTO);
        when(roomRepository.findWithLockById(10L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> reservationService.create(user,
                new CreateReservationRequest(10L, LocalDate.now().plusDays(7), LocalDate.now().plusDays(10), 2)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("La habitación no está disponible actualmente");
    }

    @Test
    void create_habitacionInexistente_lanzaNotFound() {
        when(roomRepository.findWithLockById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.create(user,
                new CreateReservationRequest(99L, LocalDate.now().plusDays(7), LocalDate.now().plusDays(10), 2)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_enviaCorreoDeConfirmacion() {
        var checkIn = LocalDate.now().plusDays(7);
        var checkOut = LocalDate.now().plusDays(10);
        when(roomRepository.findWithLockById(10L)).thenReturn(Optional.of(room));
        when(reservationRepository.existsOverlapping(anyLong(), any(), any(), any(), any())).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        reservationService.create(user, new CreateReservationRequest(10L, checkIn, checkOut, 2));

        verify(mailService).sendReservationConfirmationEmail(
                anyString(), anyString(), anyString(), anyString(),
                any(LocalDate.class), any(LocalDate.class), anyInt(), anyLong(), any(BigDecimal.class));
    }

    @Test
    void create_falloDeCorreo_noPropagaExcepcion() {
        var checkIn = LocalDate.now().plusDays(7);
        var checkOut = LocalDate.now().plusDays(10);
        when(roomRepository.findWithLockById(10L)).thenReturn(Optional.of(room));
        when(reservationRepository.existsOverlapping(anyLong(), any(), any(), any(), any())).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        doThrow(new RuntimeException("SMTP caído")).when(mailService).sendReservationConfirmationEmail(
                anyString(), anyString(), anyString(), anyString(),
                any(LocalDate.class), any(LocalDate.class), anyInt(), anyLong(), any(BigDecimal.class));

        var result = reservationService.create(user, new CreateReservationRequest(10L, checkIn, checkOut, 2));

        assertThat(result).isNotNull();
    }

    // === findMine / findById ===

    @Test
    void findMine_devuelveReservasMapeadas() {
        var reservation = buildReservation(LocalDate.now().plusDays(7), LocalDate.now().plusDays(10),
                ReservationStatus.PENDIENTE);
        when(reservationRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(reservation));

        var result = reservationService.findMine(user);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("RSV-000123");
        assertThat(result.get(0).room().slug()).isEqualTo("deluxe-cama-grande");
    }

    @Test
    void findById_reservaAjena_lanzaForbidden() {
        var otro = Auth.builder().id(2L).name("Luis").lastName("Mora").email("luis@mail.com")
                .role(new Role(2L, RoleName.CLIENTE.name())).active(true).build();
        var reservation = buildReservation(LocalDate.now().plusDays(7), LocalDate.now().plusDays(10),
                ReservationStatus.PENDIENTE);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.findById(otro, 123L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void findById_reservaInexistente_lanzaNotFound() {
        when(reservationRepository.findWithRoomById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.findById(user, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // === flags can_edit / can_cancel ===

    @Test
    void flags_reservaLejana_sonTrue() {
        var reservation = buildReservation(LocalDate.now().plusDays(10), LocalDate.now().plusDays(12),
                ReservationStatus.PENDIENTE);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        var result = reservationService.findById(user, 123L);

        assertThat(result.canEdit()).isTrue();
        assertThat(result.canCancel()).isTrue();
    }

    @Test
    void flags_dentroDeLaVentanaDe48h_sonFalse() {
        var reservation = buildReservation(LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                ReservationStatus.PENDIENTE);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        var result = reservationService.findById(user, 123L);

        assertThat(result.canEdit()).isFalse();
        assertThat(result.canCancel()).isFalse();
    }

    @Test
    void flags_reservaCancelada_sonFalse() {
        var reservation = buildReservation(LocalDate.now().plusDays(10), LocalDate.now().plusDays(12),
                ReservationStatus.CANCELADA);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        var result = reservationService.findById(user, 123L);

        assertThat(result.canEdit()).isFalse();
        assertThat(result.canCancel()).isFalse();
    }

    // === update ===

    @Test
    void update_recalculaTotalYExcluyeLaPropiaReservaDelSolape() {
        var reservation = buildReservation(LocalDate.now().plusDays(10), LocalDate.now().plusDays(12),
                ReservationStatus.PENDIENTE);
        var newCheckIn = LocalDate.now().plusDays(20);
        var newCheckOut = LocalDate.now().plusDays(24); // 4 noches
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));
        when(roomRepository.findWithLockById(10L)).thenReturn(Optional.of(room));
        when(reservationRepository.existsOverlapping(10L, newCheckIn, newCheckOut, RoomService.BLOCKING_STATUSES, 123L))
                .thenReturn(false);

        var result = reservationService.update(user, 123L,
                new UpdateReservationRequest(newCheckIn, newCheckOut, 2));

        assertThat(result.total()).isEqualByComparingTo("960.00"); // 4 * 240
        assertThat(result.checkIn()).isEqualTo(newCheckIn);
        verify(reservationRepository).existsOverlapping(10L, newCheckIn, newCheckOut,
                RoomService.BLOCKING_STATUSES, 123L);
    }

    @Test
    void update_fueraDeVentana_lanzaConflict() {
        var reservation = buildReservation(LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                ReservationStatus.PENDIENTE);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.update(user, 123L,
                new UpdateReservationRequest(LocalDate.now().plusDays(20), LocalDate.now().plusDays(22), 2)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("La reserva ya no puede modificarse");
    }

    @Test
    void update_nuevoRangoOcupado_lanzaConflict() {
        var reservation = buildReservation(LocalDate.now().plusDays(10), LocalDate.now().plusDays(12),
                ReservationStatus.PENDIENTE);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));
        when(roomRepository.findWithLockById(10L)).thenReturn(Optional.of(room));
        when(reservationRepository.existsOverlapping(anyLong(), any(), any(), any(), anyLong()))
                .thenReturn(true);

        assertThatThrownBy(() -> reservationService.update(user, 123L,
                new UpdateReservationRequest(LocalDate.now().plusDays(20), LocalDate.now().plusDays(22), 2)))
                .isInstanceOf(ConflictException.class);
    }

    // === cancel ===

    @Test
    void cancel_dentroDeVentana_cambiaEstadoYEnviaCorreo() {
        var reservation = buildReservation(LocalDate.now().plusDays(10), LocalDate.now().plusDays(12),
                ReservationStatus.PENDIENTE);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        var result = reservationService.cancel(user, 123L, null);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(result.status()).isEqualTo("CANCELADA");
        assertThat(result.canEdit()).isFalse();
        verify(reservationRepository).save(reservation);
        verify(mailService).sendReservationCancellationEmail(
                anyString(), anyString(), anyString(), anyString(),
                any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void cancel_fueraDeVentana_lanzaConflict() {
        var reservation = buildReservation(LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                ReservationStatus.PENDIENTE);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(user, 123L, null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("La reserva ya no puede cancelarse");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void cancel_reservaYaCancelada_lanzaConflict() {
        var reservation = buildReservation(LocalDate.now().plusDays(10), LocalDate.now().plusDays(12),
                ReservationStatus.CANCELADA);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(user, 123L, null))
                .isInstanceOf(ConflictException.class);
    }

    // === recepción: confirm / check-in / check-out (HU-019) ===

    private Auth internalActor() {
        return Auth.builder()
                .id(9L).name("Recep").lastName("HMAP").email("recepcion@mail.com")
                .role(new Role(3L, RoleName.RECEPCIONISTA.name())).active(true).build();
    }

    @Test
    void confirm_reservaPendiente_pasaAConfirmada() {
        var reservation = buildReservation(LocalDate.now().plusDays(5), LocalDate.now().plusDays(7),
                ReservationStatus.PENDIENTE);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        var result = reservationService.confirm(123L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(result.status()).isEqualTo("CONFIRMADA");
        verify(reservationRepository).save(reservation);
    }

    @Test
    void confirm_reservaNoPendiente_lanzaConflict() {
        var reservation = buildReservation(LocalDate.now().plusDays(5), LocalDate.now().plusDays(7),
                ReservationStatus.CONFIRMADA);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.confirm(123L))
                .isInstanceOf(ConflictException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void checkIn_reservaConfirmada_ocupaLaHabitacion() {
        var reservation = buildReservation(LocalDate.now(), LocalDate.now().plusDays(2),
                ReservationStatus.CONFIRMADA);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        var result = reservationService.checkIn(123L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CHECK_IN);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.OCUPADA);
        assertThat(result.status()).isEqualTo("CHECK_IN");
    }

    @Test
    void checkIn_reservaNoConfirmada_lanzaConflict() {
        var reservation = buildReservation(LocalDate.now(), LocalDate.now().plusDays(2),
                ReservationStatus.PENDIENTE);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.checkIn(123L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void checkOut_reservaConCheckIn_liberaLaHabitacion() {
        room.setStatus(RoomStatus.OCUPADA);
        var reservation = buildReservation(LocalDate.now().minusDays(2), LocalDate.now(),
                ReservationStatus.CHECK_IN);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        var result = reservationService.checkOut(123L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CHECK_OUT);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.DISPONIBLE);
        assertThat(result.status()).isEqualTo("CHECK_OUT");
    }

    @Test
    void checkOut_reservaSinCheckIn_lanzaConflict() {
        var reservation = buildReservation(LocalDate.now().minusDays(2), LocalDate.now(),
                ReservationStatus.CONFIRMADA);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.checkOut(123L))
                .isInstanceOf(ConflictException.class);
    }

    // === recepción: edición y cancelación internas (HU-021/HU-022) ===

    @Test
    void updateInterno_ignoraLaVentanaDePlazos() {
        // Reserva dentro de las 48 h: el cliente no podría, el rol interno sí.
        var reservation = buildReservation(LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                ReservationStatus.PENDIENTE);
        var newCheckIn = LocalDate.now().plusDays(20);
        var newCheckOut = LocalDate.now().plusDays(23);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));
        when(roomRepository.findWithLockById(10L)).thenReturn(Optional.of(room));
        when(reservationRepository.existsOverlapping(10L, newCheckIn, newCheckOut,
                RoomService.BLOCKING_STATUSES, 123L)).thenReturn(false);

        var result = reservationService.update(internalActor(), 123L,
                new UpdateReservationRequest(newCheckIn, newCheckOut, 2));

        assertThat(result.checkIn()).isEqualTo(newCheckIn);
    }

    @Test
    void cancelInterno_sinMotivo_lanzaBadRequest() {
        var reservation = buildReservation(LocalDate.now().plusDays(5), LocalDate.now().plusDays(7),
                ReservationStatus.PENDIENTE);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(internalActor(), 123L,
                new CancelReservationRequest("   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("El motivo de la cancelación es obligatorio");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void cancelInterno_conMotivo_ignoraLaVentanaYCancela() {
        // Dentro de las 48 h: el rol interno puede cancelar igualmente (con motivo).
        var reservation = buildReservation(LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                ReservationStatus.PENDIENTE);
        when(reservationRepository.findWithRoomById(123L)).thenReturn(Optional.of(reservation));

        var result = reservationService.cancel(internalActor(), 123L,
                new CancelReservationRequest("No-show del huésped"));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(result.status()).isEqualTo("CANCELADA");
        verify(reservationRepository).save(reservation);
    }

    // === recepción: reserva manual (HU-020/HU-037) ===

    @Test
    void createManual_cuentaNueva_creaReservaYEnviaCorreoConCredenciales() {
        var checkIn = LocalDate.now().plusDays(7);
        var checkOut = LocalDate.now().plusDays(9);
        var guestData = new ManualReservationRequest.Guest("Carlos", "Ruiz", "carlos@mail.com", "888");
        var request = new ManualReservationRequest(10L, checkIn, checkOut, 2, guestData);

        var guestAccount = Auth.builder().id(50L).name("Carlos").lastName("Ruiz").email("carlos@mail.com")
                .role(new Role(2L, RoleName.CLIENTE.name())).active(true).build();
        when(userService.findOrCreateGuest("Carlos", "Ruiz", "carlos@mail.com", "888"))
                .thenReturn(new UserService.ProvisionedGuest(guestAccount, "TempPass1234"));
        when(roomRepository.findWithLockById(10L)).thenReturn(Optional.of(room));
        when(reservationRepository.existsOverlapping(10L, checkIn, checkOut,
                RoomService.BLOCKING_STATUSES, null)).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(77L);
            return r;
        });

        var result = reservationService.createManual(request);

        assertThat(result.status()).isEqualTo("PENDIENTE");
        assertThat(result.guest().email()).isEqualTo("carlos@mail.com");
        verify(mailService).sendManualReservationEmail(
                eq("carlos@mail.com"), eq("Carlos"), anyString(), anyString(),
                any(LocalDate.class), any(LocalDate.class), anyInt(), anyLong(),
                any(BigDecimal.class), eq("TempPass1234"));
    }

    // === recepción: hoy / calendario / búsqueda (HU-017/HU-018/HU-023/HU-024) ===

    @Test
    void findToday_agrupaEntradasYSalidas() {
        var arriving = buildReservation(LocalDate.now(), LocalDate.now().plusDays(2),
                ReservationStatus.CONFIRMADA);
        var departing = buildReservation(LocalDate.now().minusDays(2), LocalDate.now(),
                ReservationStatus.CHECK_IN);
        when(reservationRepository.findByCheckInAndStatusInOrderByCheckInAsc(any(LocalDate.class), any()))
                .thenReturn(List.of(arriving));
        when(reservationRepository.findByCheckOutAndStatusInOrderByCheckOutAsc(any(LocalDate.class), any()))
                .thenReturn(List.of(departing));

        var result = reservationService.findToday();

        assertThat(result.checkIns()).hasSize(1);
        assertThat(result.checkOuts()).hasSize(1);
    }

    @Test
    void findCalendar_rangoInvertido_lanzaBadRequest() {
        assertThatThrownBy(() -> reservationService.findCalendar(
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(1)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void search_mapeaLaPaginaAPageResponse() {
        var reservation = buildReservation(LocalDate.now().plusDays(5), LocalDate.now().plusDays(7),
                ReservationStatus.CONFIRMADA);
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(reservationRepository.search(isNull(), isNull(), isNull(), isNull(), isNull(),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(reservation), pageable, 1));

        var result = reservationService.search(null, null, null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isZero();
    }
}
