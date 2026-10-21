package com.easyeat.auth.security;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class JwtService {

    public String generateToken(User user) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public UUID extractUserId(String token) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public UserRole extractRole(String token) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public UUID extractTenantId(String token) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Integer extractTokenVersion(String token) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public boolean validateToken(String token) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
