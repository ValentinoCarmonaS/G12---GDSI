package com.easyeat.auth.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.dto.RegisterRequest;
import com.easyeat.auth.dto.UserSummaryResponse;
import com.easyeat.auth.exception.EmailAlreadyRegisteredException;
import com.easyeat.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

    public com.easyeat.auth.dto.AuthTokenResponse login(com.easyeat.auth.dto.LoginRequest request, String clientIp) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
