package com.hmap.backend.notification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.hmap.backend.reservation.entity.Reservation;

/**
 * Envío de correos transaccionales del sistema.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Envía el enlace de recuperación de contraseña (HU-038).
     */
    public void sendPasswordResetEmail(String to, String resetLink) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Recuperación de contraseña - Hotel Manuel Antonio Park");
        message.setText("""
                Hola,

                Recibimos una solicitud para restablecer tu contraseña.
                Haz clic en el siguiente enlace para crear una nueva (válido por tiempo limitado):

                %s

                Si no solicitaste este cambio, puedes ignorar este correo.

                Saludos,
                Hotel Manuel Antonio Park
                """.formatted(resetLink));

        mailSender.send(message);
    }

    /**
     * Envía los detalles de la reserva recién creada (HU-035).
     * Versión asíncrona: no bloquea la respuesta HTTP.
     */
    @Async
    public void sendReservationConfirmationEmailAsync(Reservation reservation) {
        var guest = reservation.getUser();
        try {
            sendReservationConfirmationEmail(
                    guest.getEmail(),
                    guest.getName(),
                    "RSV-%06d".formatted(reservation.getId()),
                    reservation.getRoom().getName(),
                    reservation.getCheckIn(),
                    reservation.getCheckOut(),
                    reservation.getGuests(),
                    reservation.getNights(),
                    reservation.getTotal());
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de confirmación de la reserva {}: {}",
                    reservation.getId(), e.getMessage());
        }
    }

    /**
     * Envía los detalles de una reserva manual (HU-037).
     * Versión asíncrona: no bloquea la respuesta HTTP.
     */
    @Async
    public void sendManualReservationEmailAsync(Reservation reservation, String temporaryPassword) {
        var guest = reservation.getUser();
        try {
            sendManualReservationEmail(
                    guest.getEmail(),
                    guest.getName(),
                    "RSV-%06d".formatted(reservation.getId()),
                    reservation.getRoom().getName(),
                    reservation.getCheckIn(),
                    reservation.getCheckOut(),
                    reservation.getGuests(),
                    reservation.getNights(),
                    reservation.getTotal(),
                    temporaryPassword);
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de la reserva manual {}: {}",
                    reservation.getId(), e.getMessage());
        }
    }

    /**
     * Notifica la cancelación de una reserva (HU-036).
     * Versión asíncrona: no bloquea la respuesta HTTP.
     */
    @Async
    public void sendReservationCancellationEmailAsync(Reservation reservation) {
        var guest = reservation.getUser();
        try {
            sendReservationCancellationEmail(
                    guest.getEmail(),
                    guest.getName(),
                    "RSV-%06d".formatted(reservation.getId()),
                    reservation.getRoom().getName(),
                    reservation.getCheckIn(),
                    reservation.getCheckOut());
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de cancelación de la reserva {}: {}",
                    reservation.getId(), e.getMessage());
        }
    }

    /**
     * Envía un correo de bienvenida cuando se confirma una reserva pendiente.
     * Versión asíncrona: no bloquea la respuesta HTTP.
     */
    @Async
    public void sendReservationWelcomeEmailAsync(Reservation reservation) {
        var guest = reservation.getUser();
        try {
            sendReservationWelcomeEmail(
                    guest.getEmail(),
                    guest.getName(),
                    "RSV-%06d".formatted(reservation.getId()),
                    reservation.getRoom().getName(),
                    reservation.getCheckIn(),
                    reservation.getCheckOut(),
                    reservation.getGuests(),
                    reservation.getNights(),
                    reservation.getTotal());
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de bienvenida de la reserva {}: {}",
                    reservation.getId(), e.getMessage());
        }
    }

    /**
     * Envía los detalles de la reserva recién creada (HU-035).
     */
    public void sendReservationConfirmationEmail(String to, String guestName, String code,
                                                 String roomName, LocalDate checkIn, LocalDate checkOut,
                                                 int guests, long nights, BigDecimal total) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Confirmación de reserva %s - Hotel Manuel Antonio Park".formatted(code));
        message.setText("""
                Hola %s,

                ¡Gracias por tu reserva! Estos son los detalles de tu estancia:

                Código de reserva: %s
                Habitación: %s
                Entrada: %s
                Salida: %s
                Huéspedes: %d
                Noches: %d
                Total: $%s USD

                Te esperamos en el Hotel Manuel Antonio Park.

                Saludos,
                Hotel Manuel Antonio Park
                """.formatted(guestName, code, roomName,
                DATE_FORMAT.format(checkIn), DATE_FORMAT.format(checkOut),
                guests, nights, total));

        mailSender.send(message);
    }

    /**
     * Envía los detalles de una reserva creada por recepción (HU-037). Si la
     * cuenta del huésped se acaba de crear, incluye la contraseña temporal para
     * que pueda acceder al portal; si ya existía, {@code temporaryPassword} es null.
     */
    public void sendManualReservationEmail(String to, String guestName, String code,
                                           String roomName, LocalDate checkIn, LocalDate checkOut,
                                           int guests, long nights, BigDecimal total,
                                           String temporaryPassword) {
        var credentialsBlock = temporaryPassword == null ? "" : """

                Te creamos una cuenta para que gestiones tu reserva en línea:
                Usuario: %s
                Contraseña temporal: %s
                Te recomendamos cambiarla al iniciar sesión.
                """.formatted(to, temporaryPassword);

        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Reserva %s - Hotel Manuel Antonio Park".formatted(code));
        message.setText("""
                Hola %s,

                Hemos registrado una reserva a tu nombre. Estos son los detalles:

                Código de reserva: %s
                Habitación: %s
                Entrada: %s
                Salida: %s
                Huéspedes: %d
                Noches: %d
                Total: $%s USD
                %s
                Te esperamos en el Hotel Manuel Antonio Park.

                Saludos,
                Hotel Manuel Antonio Park
                """.formatted(guestName, code, roomName,
                DATE_FORMAT.format(checkIn), DATE_FORMAT.format(checkOut),
                guests, nights, total, credentialsBlock));

        mailSender.send(message);
    }

    /**
     * Notifica la cancelación de una reserva (HU-036).
     */
    public void sendReservationCancellationEmail(String to, String guestName, String code,
                                                 String roomName, LocalDate checkIn, LocalDate checkOut) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Cancelación de reserva %s - Hotel Manuel Antonio Park".formatted(code));
        message.setText("""
                Hola %s,

                Tu reserva ha sido cancelada correctamente:

                Código de reserva: %s
                Habitación: %s
                Entrada: %s
                Salida: %s

                Si no solicitaste esta cancelación o necesitas ayuda,
                contáctanos respondiendo a este correo.

                Saludos,
                Hotel Manuel Antonio Park
                """.formatted(guestName, code, roomName,
                DATE_FORMAT.format(checkIn), DATE_FORMAT.format(checkOut)));

        mailSender.send(message);
    }

    /**
     * Envía un correo de bienvenida cuando se confirma una reserva pendiente.
     */
    public void sendReservationWelcomeEmail(String to, String guestName, String code,
                                            String roomName, LocalDate checkIn, LocalDate checkOut,
                                            int guests, long nights, BigDecimal total) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("¡Bienvenido! Reserva confirmada %s - Hotel Manuel Antonio Park".formatted(code));
        message.setText("""
                Hola %s,

                ¡Su reserva ha sido confirmada exitosamente!

                El Hotel Manuel Antonio Park le da la bienvenida.

                Estos son los detalles de su estancia:

                Código de reserva: %s
                Habitación: %s
                Entrada: %s
                Salida: %s
                Huéspedes: %d
                Noches: %d
                Total: $%s USD

                Si tiene alguna pregunta o necesita
                asistencia adicional, no dude en contactarnos.


                Saludos,
                Hotel Manuel Antonio Park
                """.formatted(guestName, code, roomName,
                DATE_FORMAT.format(checkIn), DATE_FORMAT.format(checkOut),
                guests, nights, total));

        mailSender.send(message);
    }
}
