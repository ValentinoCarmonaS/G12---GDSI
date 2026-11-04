package com.easyeat.profile.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    @DisplayName("Should resolve explicit sender address when configured")
    void shouldResolveCustomAppMailFromWhenProvided() {
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, "soporte@easyeat.com");
        assertThat(sender.getFromEmail()).isEqualTo("soporte@easyeat.com");
    }

    @Test
    @DisplayName("Should fallback to default noreply@easyeat.com when sender is null or blank")
    void shouldFallbackToDefaultWhenFromEmailIsNullOrEmpty() {
        SmtpEmailSender senderNull = new SmtpEmailSender(mailSender, null);
        assertThat(senderNull.getFromEmail()).isEqualTo("noreply@easyeat.com");

        SmtpEmailSender senderBlank = new SmtpEmailSender(mailSender, "   ");
        assertThat(senderBlank.getFromEmail()).isEqualTo("noreply@easyeat.com");
    }

    @Test
    @DisplayName("Should construct and send SimpleMailMessage with correct recipient, subject and verification link")
    void shouldSendVerificationEmailWithExpectedParameters() {
        String from = "soporte@easyeat.com";
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, from);

        String recipient = "comensal@example.com";
        String link = "https://easyeat-frontend.onrender.com/verificar-email?token=abc-123";

        sender.sendVerificationEmail(recipient, link);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getFrom()).isEqualTo(from);
        assertThat(sentMessage.getTo()).containsExactly(recipient);
        assertThat(sentMessage.getSubject()).isEqualTo("EasyEat - Verificación de Cambio de Correo Electrónico");
        assertThat(sentMessage.getText()).contains(link);
        assertThat(sentMessage.getText()).contains("24 horas");
    }

    @Test
    @DisplayName("Should handle null JavaMailSender gracefully without throwing")
    void shouldHandleNullMailSenderGracefullyWithoutThrowing() {
        SmtpEmailSender sender = new SmtpEmailSender(null, "noreply@easyeat.com");

        assertThatCode(() -> sender.sendVerificationEmail("user@example.com", "http://verification.link"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should catch and handle MailException without bubbling up to caller")
    void shouldCatchAndLogExceptionWhenMailSenderThrows() {
        doThrow(new MailSendException("SMTP relay unreachable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        SmtpEmailSender sender = new SmtpEmailSender(mailSender, "noreply@easyeat.com");

        assertThatCode(() -> sender.sendVerificationEmail("user@example.com", "http://verification.link"))
                .doesNotThrowAnyException();

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}

