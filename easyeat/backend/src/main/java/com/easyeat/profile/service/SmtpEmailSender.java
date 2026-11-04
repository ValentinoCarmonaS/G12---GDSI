package com.easyeat.profile.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Production implementation of {@link EmailSender} that dispatches verification emails
 * via {@link JavaMailSender}.
 */
@Component
@Profile("prod")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public SmtpEmailSender(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${app.mail.from:${spring.mail.username:noreply@easyeat.com}}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail != null && !fromEmail.isBlank() ? fromEmail.trim() : "noreply@easyeat.com";
    }

    public String getFromEmail() {
        return fromEmail;
    }

    @Async
    @Override
    public void sendVerificationEmail(String toEmail, String verificationUrl) {
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Skipping verification email dispatch to: {}", toEmail);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("EasyEat - Verificación de Cambio de Correo Electrónico");
            message.setText("Hola,\n\nHas solicitado cambiar tu correo electrónico en EasyEat. Haz clic en el siguiente enlace para confirmar la verificación:\n\n"
                    + verificationUrl + "\n\nEste enlace expirará en 24 horas.\n\nSi no solicitaste este cambio, puedes ignorar este correo.");

            mailSender.send(message);
            log.info("Verification email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", toEmail, e);
        }
    }
}

