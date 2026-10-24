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
import com.easyeat.auth.security.SecurityAuditLogger;
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
    private final SecurityAuditLogger securityAuditLogger;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RateLimitingService rateLimitingService) {
        this(userRepository, passwordEncoder, jwtService, rateLimitingService, new SecurityAuditLogger(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RateLimitingService rateLimitingService,
            SecurityAuditLogger securityAuditLogger) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimitingService = rateLimitingService;
        this.securityAuditLogger = securityAuditLogger;
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

        securityAuditLogger.logUserRegistered(normalizedEmail, user.getRole().name(), user.getStatus().name());

        return new UserSummaryResponse("Account created successfully.");
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request, String clientIp) {
        String normalizedEmail = request.email().trim().toLowerCase();

        // 1. Rate limiting check occurs BEFORE password verification to prevent oracle attacks
        if (rateLimitingService.isLoginLocked(normalizedEmail, clientIp)) {
            securityAuditLogger.logLockout(normalizedEmail, clientIp, 15);
            throw new RateLimitExceededException("Too many failed login attempts. Please try again in 15 minutes.");
        }

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        // 2. Timing attack mitigation: if user does not exist, run BCrypt against DUMMY_HASH
        if (userOpt.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            rateLimitingService.recordLoginFailure(normalizedEmail, clientIp);
            securityAuditLogger.logLoginFailure(normalizedEmail, clientIp, "USER_NOT_FOUND");
            throw new InvalidCredentialsException("The provided email or password is incorrect.");
        }

        User user = userOpt.get();

        // 3. Password verification
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimitingService.recordLoginFailure(normalizedEmail, clientIp);
            securityAuditLogger.logLoginFailure(normalizedEmail, clientIp, "BAD_CREDENTIALS");
            throw new InvalidCredentialsException("The provided email or password is incorrect.");
        }

        // 4. Status validation
        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.PENDING_FIRST_LOGIN) {
            securityAuditLogger.logLoginFailure(normalizedEmail, clientIp, "ACCOUNT_SUSPENDED");
            throw new AccountSuspendedException("The account has been suspended or is not authorized to operate.");
        }

        // 5. Successful login resets failure counter, increments token_version (invalidating prior sessions), and issues JWT
        rateLimitingService.resetLoginAttempts(normalizedEmail, clientIp);
        user.incrementTokenVersion();
        userRepository.save(user);
        String token = jwtService.generateToken(user);

        securityAuditLogger.logLoginSuccess(normalizedEmail, user.getRole().name(), clientIp, user.getId());

        return new AuthTokenResponse(token);
    }

    @Transactional
    public void logout(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User account not found."));
        user.incrementTokenVersion();
        userRepository.save(user);
        securityAuditLogger.logLogout(user.getEmail(), user.getRole().name(), userId);
    }
}
