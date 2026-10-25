package com.easyeat.profile.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class LoggingEmailSenderTest {

    private final LoggingEmailSender emailSender = new LoggingEmailSender();

    @Test
    @DisplayName("Should log verification email details without throwing any exception")
    void sendVerificationEmail_success() {
        String toEmail = "diner@example.com";
        String verificationUrl = "http://localhost:3000/verificar-email?token=rawToken123";

        assertThatCode(() -> emailSender.sendVerificationEmail(toEmail, verificationUrl))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle null or empty values gracefully without throwing")
    void sendVerificationEmail_nullGraceful() {
        assertThatCode(() -> emailSender.sendVerificationEmail(null, null))
                .doesNotThrowAnyException();
    }
}
