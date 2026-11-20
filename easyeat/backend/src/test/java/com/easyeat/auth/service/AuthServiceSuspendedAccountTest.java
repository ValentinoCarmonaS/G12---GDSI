package com.easyeat.auth.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.dto.LoginRequest;
import com.easyeat.auth.exception.InvalidCredentialsException;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
import com.easyeat.auth.security.RateLimitingService;
import com.easyeat.auth.security.SecurityAuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceSuspendedAccountTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private SecurityAuditLogger securityAuditLogger;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                rateLimitingService,
                securityAuditLogger
        );
    }

    @Test
    void loginWithSuspendedAccount_runsPasswordCheck_logsAudit_andThrowsGenericInvalidCredentials() {
        User suspendedUser = new User();
        suspendedUser.setId(UUID.randomUUID());
        suspendedUser.setEmail("chef@easyeat.com");
        suspendedUser.setPasswordHash("hashed-password");
        suspendedUser.setRole(UserRole.CHEF);
        suspendedUser.setStatus(UserStatus.SUSPENDED);

        when(rateLimitingService.isLoginLocked("chef@easyeat.com", "127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("chef@easyeat.com")).thenReturn(Optional.of(suspendedUser));
        when(passwordEncoder.matches("ValidPassword123!", "hashed-password")).thenReturn(true);

        InvalidCredentialsException ex = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("chef@easyeat.com", "ValidPassword123!"), "127.0.0.1")
        );

        assertEquals("The provided email or password is incorrect.", ex.getMessage());
        verify(passwordEncoder).matches("ValidPassword123!", "hashed-password");
        verify(securityAuditLogger).logLoginFailure("chef@easyeat.com", "127.0.0.1", "ACCOUNT_SUSPENDED");
    }

    @Test
    void loginWithUnverifiedAccount_runsPasswordCheck_logsAudit_andThrowsGenericInvalidCredentials() {
        User unverifiedUser = new User();
        unverifiedUser.setId(UUID.randomUUID());
        unverifiedUser.setEmail("unverified@easyeat.com");
        unverifiedUser.setPasswordHash("hashed-password");
        unverifiedUser.setRole(UserRole.ENCARGADO);
        unverifiedUser.setStatus(UserStatus.UNVERIFIED);

        when(rateLimitingService.isLoginLocked("unverified@easyeat.com", "127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("unverified@easyeat.com")).thenReturn(Optional.of(unverifiedUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        InvalidCredentialsException ex = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("unverified@easyeat.com", "ValidPassword123!"), "127.0.0.1")
        );

        assertEquals("The provided email or password is incorrect.", ex.getMessage());
        verify(securityAuditLogger).logLoginFailure("unverified@easyeat.com", "127.0.0.1", "ACCOUNT_SUSPENDED");
    }
}
