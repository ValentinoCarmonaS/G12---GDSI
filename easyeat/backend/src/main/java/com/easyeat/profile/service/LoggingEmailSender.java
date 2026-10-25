package com.easyeat.profile.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Development and testing stub for {@link EmailSender} that prints raw verification URLs to console logs at INFO level.
 */
@Component
@Profile("dev | test")
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void sendVerificationEmail(String toEmail, String verificationUrl) {
        log.info("[LoggingEmailSender] Dispatching verification email to: {}. Verification URL: {}", toEmail, verificationUrl);
    }
}
