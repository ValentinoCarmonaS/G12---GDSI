package com.easyeat.auth.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.dto.AuthTokenResponse;
import com.easyeat.auth.dto.LoginRequest;
import com.easyeat.auth.exception.AccountSuspendedException;
import com.easyeat.auth.exception.InvalidCredentialsException;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
import com.easyeat.auth.security.RateLimitingService;
import com.easyeat.auth.security.SecurityAuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
    @DisplayName("logout - increments token_version atomically and saves user")
    void shouldIncrementTokenVersionOnLogout() {
        UUID userId = UUID.randomUUID();
        User user = new User("chef@restaurant.com", "hash", UserRole.CHEF);
        user.setId(userId);
        user.setTokenVersion(1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        authService.logout(userId);

        assertThat(user.getTokenVersion()).isEqualTo(2);
        verify(userRepository).save(user);
        verify(securityAuditLogger).logLogout("chef@restaurant.com", "CHEF", userId);
    }

    @Test
    @DisplayName("logout - throws InvalidCredentialsException when user not found")
    void shouldThrowExceptionWhenUserNotFoundOnLogout() {
        UUID nonExistentId = UUID.randomUUID();
        when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout(nonExistentId))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("not found");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login - increments token_version to invalidate previous active sessions")
    void shouldIncrementTokenVersionOnLogin() {
        String email = "encargada@restaurant.com";
        String password = "Password123";
        User user = new User(email, "hashedPassword", UserRole.ENCARGADO);
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(0);

        when(rateLimitingService.isLoginLocked(email, "127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "hashedPassword")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("new.jwt.token");

        AuthTokenResponse response = authService.login(new LoginRequest(email, password), "127.0.0.1");

        assertThat(response.accessToken()).isEqualTo("new.jwt.token");
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(userRepository).save(user);
        verify(securityAuditLogger).logLoginSuccess(eq(email), eq("ENCARGADO"), eq("127.0.0.1"), eq(user.getId()));
    }

    @Test
    @DisplayName("login - chef authentication emits structured audit log and verifies role CHEF (T024, CHF-1.1.1)")
    void shouldEmitAuditLogOnChefLogin() {
        String email = "carlos.chef@comedorcentral.com";
        String password = "ChefPassword123";
        User user = new User(email, "hashedChefPassword", UserRole.CHEF);
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(1);

        when(rateLimitingService.isLoginLocked(email, "192.168.1.50")).thenReturn(false);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "hashedChefPassword")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("mock.chef.token");

        AuthTokenResponse response = authService.login(new LoginRequest(email, password), "192.168.1.50");

        assertThat(response.accessToken()).isEqualTo("mock.chef.token");
        verify(securityAuditLogger).logLoginSuccess(eq(email), eq("CHEF"), eq("192.168.1.50"), eq(user.getId()));
    }

    @Test
    @DisplayName("login - throws AccountSuspendedException and logs failure when chef account is suspended (T025, CHF-1.1.1)")
    void shouldThrowAndLogWhenChefAccountIsSuspended() {
        String email = "suspended.chef@comedorcentral.com";
        String password = "ChefPassword123";
        User user = new User(email, "hashedChefPassword", UserRole.CHEF);
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.SUSPENDED);

        when(rateLimitingService.isLoginLocked(email, "192.168.1.50")).thenReturn(false);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "hashedChefPassword")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, password), "192.168.1.50"))
                .isInstanceOf(AccountSuspendedException.class)
                .hasMessageContaining("suspended");

        verify(securityAuditLogger).logLoginFailure(eq(email), eq("192.168.1.50"), eq("ACCOUNT_SUSPENDED"));
    }
}
