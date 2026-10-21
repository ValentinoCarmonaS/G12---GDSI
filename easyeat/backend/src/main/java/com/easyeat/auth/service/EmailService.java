package com.easyeat.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public EmailService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${spring.mail.username:noreply@easyeat.com}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail != null && !fromEmail.isBlank() ? fromEmail : "noreply@easyeat.com";
    }

    @Async
    public void sendPasswordResetEmail(String to, String resetLink) {
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Skipping email dispatch to: {}", to);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject("EasyEat - Restablecimiento de Contraseña");
            message.setText("Hola,\n\nHas solicitado restablecer tu contraseña en EasyEat. Haz clic en el siguiente enlace para continuar:\n\n"
                    + resetLink + "\n\nEste enlace expirará en 15 minutos.\n\nSi no solicitaste este cambio, puedes ignorar este correo.");

            mailSender.send(message);
            log.info("Password reset email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", to, e);
        }
    }
}
