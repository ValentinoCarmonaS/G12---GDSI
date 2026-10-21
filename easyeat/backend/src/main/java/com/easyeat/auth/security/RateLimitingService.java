package com.easyeat.auth.security;

import org.springframework.stereotype.Service;

@Service
public class RateLimitingService {

    public boolean isLoginLocked(String email, String ip) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void recordLoginFailure(String email, String ip) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void resetLoginAttempts(String email, String ip) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
