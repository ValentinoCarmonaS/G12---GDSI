package com.easyeat.auth.security;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;
    private final Clock clock;

    public JwtService(
            @Value("${app.security.jwt.secret:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef}") String secret,
            @Value("${app.security.jwt.expiration-ms:28800000}") long expirationMs,
            Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.clock = clock;
    }

    // Default constructor for testing convenience
    public JwtService() {
        this("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 28800000L, Clock.systemUTC());
    }

    public String generateToken(User user) {
        Instant now = clock.instant();
        Instant expiry = now.plusMillis(expirationMs);

        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("user_id", user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("token_version", user.getTokenVersion() == null ? 0 : user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key);

        if (user.getEmail() != null) {
            builder.claim("email", user.getEmail());
        }

        if (user.getTenantId() != null) {
            builder.claim("tenant_id", user.getTenantId().toString());
        }

        return builder.compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).getSubject());
    }

    public UserRole extractRole(String token) {
        return UserRole.valueOf(extractAllClaims(token).get("role", String.class));
    }

    public UUID extractTenantId(String token) {
        String tenantIdStr = extractAllClaims(token).get("tenant_id", String.class);
        return tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
    }

    public Integer extractTokenVersion(String token) {
        return extractAllClaims(token).get("token_version", Integer.class);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Date expiration = claims.getExpiration();
            return expiration != null && !expiration.before(Date.from(clock.instant()));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
