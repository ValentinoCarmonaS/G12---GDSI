package com.easyeat.auth.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitingService {

    private final Cache<String, AtomicInteger> emailAttempts;
    private final Cache<String, AtomicInteger> ipAttempts;

    public static final int MAX_EMAIL_ATTEMPTS = 5;
    public static final int MAX_IP_ATTEMPTS = 50;

    public RateLimitingService() {
        this.emailAttempts = Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .build();

        this.ipAttempts = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
    }

    public boolean isLoginLocked(String email, String ip) {
        if (email != null && !email.isBlank()) {
            AtomicInteger count = emailAttempts.getIfPresent(normalize(email));
            if (count != null && count.get() >= MAX_EMAIL_ATTEMPTS) {
                return true;
            }
        }

        if (ip != null && !ip.isBlank()) {
            AtomicInteger count = ipAttempts.getIfPresent(ip.trim());
            if (count != null && count.get() >= MAX_IP_ATTEMPTS) {
                return true;
            }
        }

        return false;
    }

    public void recordLoginFailure(String email, String ip) {
        if (email != null && !email.isBlank()) {
            emailAttempts.asMap()
                    .computeIfAbsent(normalize(email), k -> new AtomicInteger(0))
                    .incrementAndGet();
        }

        if (ip != null && !ip.isBlank()) {
            ipAttempts.asMap()
                    .computeIfAbsent(ip.trim(), k -> new AtomicInteger(0))
                    .incrementAndGet();
        }
    }

    public void resetLoginAttempts(String email, String ip) {
        if (email != null && !email.isBlank()) {
            emailAttempts.invalidate(normalize(email));
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
