package com.easyeat.auth.cache;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("UserSessionCacheTest (T053, FR-005, SC-005, D-05, D-06)")
class UserSessionCacheTest {

    private UserRepository userRepository;
    private UserSessionCache cache;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userRepository = mock(UserRepository.class);
        cache = new UserSessionCache(userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Exposes bounded Caffeine limits (MAX_SIZE=2000, TTL_MINUTES=5)")
    void shouldExposeBoundedConfigurationConstants() {
        assertThat(UserSessionCache.MAX_SIZE).isEqualTo(2000L);
        assertThat(UserSessionCache.TTL_MINUTES).isEqualTo(5L);
    }

    @Test
    @DisplayName("Returns cached User on repeated reads without querying repository again")
    void shouldCachePresentUserAcrossRepeatedReads() {
        UUID userId = UUID.randomUUID();
        User user = new User("diner@easyeat.com", "hash", UserRole.COMENSAL);
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Optional<User> first = cache.getById(userId);
        Optional<User> second = cache.getById(userId);

        assertThat(first).contains(user);
        assertThat(second).contains(user);
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("Does not cache Optional.empty() so concurrent registrations are never blocked")
    void shouldNotCacheEmptyOptional() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(cache.getById(userId)).isEmpty();
        assertThat(cache.getById(userId)).isEmpty();

        verify(userRepository, times(2)).findById(userId);
    }

    @Test
    @DisplayName("Synchronous evict forces immediate reload from repository")
    void shouldReloadFromRepositoryAfterSynchronousEvict() {
        UUID userId = UUID.randomUUID();
        User active = new User("staff@easyeat.com", "hash", UserRole.CHEF);
        active.setId(userId);
        active.setStatus(UserStatus.ACTIVE);

        User suspended = new User("staff@easyeat.com", "hash", UserRole.CHEF);
        suspended.setId(userId);
        suspended.setStatus(UserStatus.SUSPENDED);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(active))
                .thenReturn(Optional.of(suspended));

        assertThat(cache.getById(userId)).get().extracting(User::getStatus).isEqualTo(UserStatus.ACTIVE);
        cache.evict(userId);
        assertThat(cache.getById(userId)).get().extracting(User::getStatus).isEqualTo(UserStatus.SUSPENDED);
        verify(userRepository, times(2)).findById(userId);
    }

    @Test
    @DisplayName("JwtAuthenticationFilter uses UserSessionCache on hit and rejects 401/403 immediately after evict")
    void jwtFilterUsesCacheAndRejectsImmediatelyAfterEviction() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtService, userRepository, new ObjectMapper(), cache);

        UUID userId = UUID.randomUUID();
        String token = "valid.jwt.token";
        User active = new User("user@easyeat.com", "hash", UserRole.COMENSAL);
        active.setId(userId);
        active.setStatus(UserStatus.ACTIVE);
        active.setTokenVersion(1);

        User suspended = new User("user@easyeat.com", "hash", UserRole.COMENSAL);
        suspended.setId(userId);
        suspended.setStatus(UserStatus.SUSPENDED);
        suspended.setTokenVersion(1);

        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRole(token)).thenReturn(UserRole.COMENSAL);
        when(jwtService.extractTokenVersion(token)).thenReturn(1);
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(active))
                .thenReturn(Optional.of(suspended));

        // 1st request: cache miss -> 1 DB read
        MockHttpServletRequest req1 = new MockHttpServletRequest();
        req1.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilterInternal(req1, res1, new MockFilterChain());
        assertThat(res1.getStatus()).isEqualTo(200);

        // 2nd request: cache hit -> 0 extra DB reads
        SecurityContextHolder.clearContext();
        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilterInternal(req2, res2, new MockFilterChain());
        assertThat(res2.getStatus()).isEqualTo(200);
        verify(userRepository, times(1)).findById(userId);

        // Evict and verify immediate 403 rejection
        cache.evict(userId);
        SecurityContextHolder.clearContext();
        MockHttpServletRequest req3 = new MockHttpServletRequest();
        req3.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res3 = new MockHttpServletResponse();
        filter.doFilterInternal(req3, res3, new MockFilterChain());
        assertThat(res3.getStatus()).isEqualTo(403);
        verify(userRepository, times(2)).findById(userId);
    }
}
