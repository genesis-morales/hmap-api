package com.hmap.backend.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envío de correos transaccionales del sistema.
 */
@Service
public class MailService {

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
}
