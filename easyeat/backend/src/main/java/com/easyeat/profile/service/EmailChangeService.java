package com.easyeat.profile.service;

import com.easyeat.auth.domain.User;
import com.easyeat.profile.dto.EmailChangeInitiatedResponse;
import com.easyeat.profile.dto.EmailChangeRequestDto;
import com.easyeat.profile.dto.EmailConfirmationRequest;
import com.easyeat.profile.dto.EmailConfirmedResponse;
import com.easyeat.profile.repository.ProfileUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailChangeService {

    private final ProfileUserRepository profileUserRepository;
    private final EmailSender emailSender;

    @Value("${app.email-verification.base-url:http://localhost:3000/verificar-email}")
    private String baseUrl;

    @Value("${app.email-verification.cooldown-seconds:60}")
    private int cooldownSeconds;

    @Value("${app.email-verification.token-validity-hours:24}")
    private int tokenValidityHours;

    public EmailChangeService(ProfileUserRepository profileUserRepository, EmailSender emailSender) {
        this.profileUserRepository = profileUserRepository;
        this.emailSender = emailSender;
    }

    @Transactional
    public EmailChangeInitiatedResponse requestEmailChange(User user, EmailChangeRequestDto request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional
    public EmailChangeInitiatedResponse resendVerificationEmail(User user) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional
    public void cancelPendingEmailChange(User user) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional
    public EmailConfirmedResponse confirmEmailChange(EmailConfirmationRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
