package com.easyeat.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    @DisplayName("Should resolve explicit sender address when configured")
    void shouldResolveCustomAppMailFromWhenProvided() {
        EmailService service = new EmailService(mailSender, "soporte@easyeat.com");
        assertThat(service.getFromEmail()).isEqualTo("soporte@easyeat.com");
    }

    @Test
    @DisplayName("Should trim whitespace from configured sender address")
    void shouldTrimWhitespaceFromConfiguredSender() {
        EmailService service = new EmailService(mailSender, "   noreply@custom.com   ");
        assertThat(service.getFromEmail()).isEqualTo("noreply@custom.com");
    }

    @Test
    @DisplayName("Should fallback to default noreply@easyeat.com when sender is null")
    void shouldFallbackToDefaultWhenFromEmailIsNull() {
        EmailService service = new EmailService(mailSender, null);
        assertThat(service.getFromEmail()).isEqualTo("noreply@easyeat.com");
    }

    @Test
    @DisplayName("Should fallback to default noreply@easyeat.com when sender is blank")
    void shouldFallbackToDefaultWhenFromEmailIsBlank() {
        EmailService service = new EmailService(mailSender, "   ");
        assertThat(service.getFromEmail()).isEqualTo("noreply@easyeat.com");
    }

    @Test
    @DisplayName("Should construct and send SimpleMailMessage with correct recipient, sender, subject and link")
    void shouldSendPasswordResetEmailWithExpectedParameters() {
        String customSender = "admin@restaurant.com";
        EmailService service = new EmailService(mailSender, customSender);

        String recipient = "comensal@example.com";
        String resetLink = "http://localhost:3000/reset-password?token=secure-token-123";

        service.sendPasswordResetEmail(recipient, resetLink);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getFrom()).isEqualTo(customSender);
        assertThat(sentMessage.getTo()).containsExactly(recipient);
        assertThat(sentMessage.getSubject()).isEqualTo("EasyEat - Restablecimiento de Contraseña");
        assertThat(sentMessage.getText()).contains(resetLink);
        assertThat(sentMessage.getText()).contains("15 minutos");
    }

    @Test
    @DisplayName("Should degrade gracefully without throwing when JavaMailSender is null")
    void shouldHandleNullMailSenderGracefullyWithoutThrowing() {
        EmailService service = new EmailService(null, "noreply@easyeat.com");

        assertThatCode(() -> service.sendPasswordResetEmail("user@example.com", "http://reset.link"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should catch and handle MailException without bubbling up to caller")
    void shouldCatchAndLogExceptionWhenMailSenderThrows() {
        doThrow(new MailSendException("SMTP relay unreachable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        EmailService service = new EmailService(mailSender, "noreply@easyeat.com");

        assertThatCode(() -> service.sendPasswordResetEmail("user@example.com", "http://reset.link"))
                .doesNotThrowAnyException();

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}
