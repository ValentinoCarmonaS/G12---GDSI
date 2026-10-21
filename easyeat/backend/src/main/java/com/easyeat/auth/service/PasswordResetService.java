package com.easyeat.auth.service;

import com.easyeat.auth.repository.PasswordResetTokenRepository;
import com.easyeat.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public void requestPasswordReset(String email) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void confirmPasswordReset(String rawToken, String newPassword) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
