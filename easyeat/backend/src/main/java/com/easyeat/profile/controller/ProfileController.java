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
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProfileResponse profile = profileService.getProfile(user);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/personal-data")
    public ResponseEntity<ProfileResponse> updatePersonalData(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PersonalDataRequest request) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProfileResponse updated = profileService.updatePersonalData(user, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/email-change/request")
    public ResponseEntity<EmailChangeInitiatedResponse> requestEmailChange(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody EmailChangeRequestDto request) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        EmailChangeInitiatedResponse response = emailChangeService.requestEmailChange(user, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/email-change/resend")
    public ResponseEntity<EmailChangeInitiatedResponse> resendVerificationEmail(
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        EmailChangeInitiatedResponse response = emailChangeService.resendVerificationEmail(user);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @DeleteMapping("/email-change/pending")
    public ResponseEntity<Void> cancelPendingEmailChange(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        emailChangeService.cancelPendingEmailChange(user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-change/confirm")
    public ResponseEntity<EmailConfirmedResponse> confirmEmailChange(
            @Valid @RequestBody EmailConfirmationRequest request) {
        EmailConfirmedResponse response = emailChangeService.confirmEmailChange(request);
        return ResponseEntity.ok(response);
    }
}
