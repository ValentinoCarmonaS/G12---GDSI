package com.easyeat.profile.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.profile.dto.*;
import com.easyeat.profile.service.EmailChangeService;
import com.easyeat.profile.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final EmailChangeService emailChangeService;

    public ProfileController(ProfileService profileService, EmailChangeService emailChangeService) {
        this.profileService = profileService;
        this.emailChangeService = emailChangeService;
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal User user) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PutMapping("/personal-data")
    public ResponseEntity<ProfileResponse> updatePersonalData(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PersonalDataRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping("/email-change/request")
    public ResponseEntity<EmailChangeInitiatedResponse> requestEmailChange(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody EmailChangeRequestDto request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping("/email-change/resend")
    public ResponseEntity<EmailChangeInitiatedResponse> resendVerificationEmail(
            @AuthenticationPrincipal User user) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @DeleteMapping("/email-change/pending")
    public ResponseEntity<Void> cancelPendingEmailChange(@AuthenticationPrincipal User user) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping("/email-change/confirm")
    public ResponseEntity<EmailConfirmedResponse> confirmEmailChange(
            @Valid @RequestBody EmailConfirmationRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
