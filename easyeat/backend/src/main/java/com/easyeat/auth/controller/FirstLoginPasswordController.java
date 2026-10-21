package com.easyeat.auth.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.dto.FirstLoginPasswordChangeRequest;
import com.easyeat.auth.exception.InvalidCredentialsException;
import com.easyeat.auth.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class FirstLoginPasswordController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public FirstLoginPasswordController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/first-login-password-change")
    public ResponseEntity<Map<String, String>> changeFirstLoginPassword(
            @Valid @RequestBody FirstLoginPasswordChangeRequest request) {

        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password and confirmation do not match.");
        }

        User user = resolveUser(request);
        if (user == null) {
            throw new InvalidCredentialsException("Authentication or registered email is required.");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current temporary password does not match.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setStatus(UserStatus.ACTIVE);
        user.incrementTokenVersion();
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password updated successfully. Account is now active."));
    }

    private User resolveUser(FirstLoginPasswordChangeRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User authenticatedUser) {
            return userRepository.findById(authenticatedUser.getId()).orElse(null);
        }

        if (request.email() != null && !request.email().isBlank()) {
            return userRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        }

        return null;
    }
}
