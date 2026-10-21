package com.easyeat.auth.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.dto.AuthTokenResponse;
import com.easyeat.auth.dto.LoginRequest;
import com.easyeat.auth.dto.RegisterRequest;
import com.easyeat.auth.dto.UserSummaryResponse;
import com.easyeat.auth.exception.AccountSuspendedException;
import com.easyeat.auth.exception.EmailAlreadyRegisteredException;
import com.easyeat.auth.exception.InvalidCredentialsException;
import com.easyeat.auth.exception.RateLimitExceededException;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
import com.easyeat.auth.security.RateLimitingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // Pre-computed BCrypt cost 12 hash for timing attack mitigation on non-existent users
    private static final String DUMMY_HASH = "$2a$12$e88n7aJm2oKszcQ6w8H7sOGlF7kM9cWJc9u2P1E2vM2S6m2rO2i6a";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimitingService rateLimitingService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RateLimitingService rateLimitingService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimitingService = rateLimitingService;
    }

    @Transactional
    public UserSummaryResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration rejected - email already exists: {}", normalizedEmail);
            throw new EmailAlreadyRegisteredException("An account with this email address already exists.");
        }

        String hashedPassword = passwordEncoder.encode(request.password());

        User user = new User(normalizedEmail, hashedPassword, UserRole.COMENSAL);
        user.setStatus(UserStatus.ACTIVE);
        user.setTenantId(null);
        user.setTokenVersion(0);

        userRepository.save(user);

        log.info("AUDIT_SECURITY: event=USER_REGISTERED, email={}, role={}, status={}",
                normalizedEmail, user.getRole(), user.getStatus());

        return new UserSummaryResponse("Account created successfully.");
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest request, String clientIp) {
        String normalizedEmail = request.email().trim().toLowerCase();

        // 1. Rate limiting check occurs BEFORE password verification to prevent oracle attacks
        if (rateLimitingService.isLoginLocked(normalizedEmail, clientIp)) {
            log.warn("AUDIT_SECURITY: event=LOGIN_LOCKOUT, email={}, ip={}", normalizedEmail, clientIp);
            throw new RateLimitExceededException("Too many failed login attempts. Please try again in 15 minutes.");
        }

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        // 2. Timing attack mitigation: if user does not exist, run BCrypt against DUMMY_HASH
        if (userOpt.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            rateLimitingService.recordLoginFailure(normalizedEmail, clientIp);
            log.warn("AUDIT_SECURITY: event=LOGIN_FAILURE, email={}, ip={}, reason=USER_NOT_FOUND", normalizedEmail, clientIp);
            throw new InvalidCredentialsException("The provided email or password is incorrect.");
        }

        User user = userOpt.get();

        // 3. Password verification
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimitingService.recordLoginFailure(normalizedEmail, clientIp);
            log.warn("AUDIT_SECURITY: event=LOGIN_FAILURE, email={}, ip={}, reason=BAD_CREDENTIALS", normalizedEmail, clientIp);
            throw new InvalidCredentialsException("The provided email or password is incorrect.");
        }

        // 4. Status validation
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("AUDIT_SECURITY: event=LOGIN_BLOCKED, email={}, ip={}, status={}", normalizedEmail, clientIp, user.getStatus());
            throw new AccountSuspendedException("The account has been suspended or is not authorized to operate.");
        }

        // 5. Successful login resets failure counter and issues JWT
        rateLimitingService.resetLoginAttempts(normalizedEmail, clientIp);
        String token = jwtService.generateToken(user);

        log.info("AUDIT_SECURITY: event=LOGIN_SUCCESS, email={}, role={}, ip={}", normalizedEmail, user.getRole(), clientIp);

        return new AuthTokenResponse(token);
    }
}
