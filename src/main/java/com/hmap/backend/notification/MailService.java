package com.hmap.backend.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Envío de correos transaccionales del sistema.
 */
@Service
public class MailService {

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
}
