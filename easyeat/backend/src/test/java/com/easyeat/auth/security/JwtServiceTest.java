package com.easyeat.auth.security;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private final long expirationMs = 28800000L; // 8 hours

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
    }

    @Test
    @DisplayName("Should generate token and extract claims accurately for Comensal")
    void shouldGenerateAndExtractClaimsForComensal() {
        User user = new User("comensal@example.com", "hash", UserRole.COMENSAL);
        UUID userId = UUID.randomUUID();
        user.setId(userId);
        user.setTokenVersion(0);
        user.setTenantId(null);

        String token = jwtService.generateToken(user);
        assertNotNull(token);

        assertEquals(userId, jwtService.extractUserId(token));
        assertEquals(UserRole.COMENSAL, jwtService.extractRole(token));
        assertNull(jwtService.extractTenantId(token));
        assertEquals(0, jwtService.extractTokenVersion(token));
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    @DisplayName("Should generate token with tenant_id claim for staff role")
    void shouldGenerateTokenWithTenantIdForStaff() {
        UUID tenantId = UUID.randomUUID();
        User staff = new User("chef@restaurant.com", "hash", UserRole.CHEF, tenantId);
        UUID staffId = UUID.randomUUID();
        staff.setId(staffId);
        staff.setTokenVersion(2);

        String token = jwtService.generateToken(staff);
        assertNotNull(token);

        assertEquals(staffId, jwtService.extractUserId(token));
        assertEquals(UserRole.CHEF, jwtService.extractRole(token));
        assertEquals(tenantId, jwtService.extractTenantId(token));
        assertEquals(2, jwtService.extractTokenVersion(token));
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    @DisplayName("Should reject tampered token signature")
    void shouldRejectTamperedToken() {
        User user = new User("user@example.com", "hash", UserRole.COMENSAL);
        user.setId(UUID.randomUUID());
        user.setTokenVersion(0);

        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.length() - 5) + "abcde";

        assertFalse(jwtService.validateToken(tampered));
    }
}
