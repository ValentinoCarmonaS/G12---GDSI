package com.easyeat.auth.security;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private UserRepository userRepository;
    private ObjectMapper objectMapper;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = mock(JwtService.class);
        userRepository = mock(UserRepository.class);
        objectMapper = new ObjectMapper();
        filter = new JwtAuthenticationFilter(jwtService, userRepository, objectMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should pass request through unauthenticated when Authorization header is absent")
    void shouldPassThroughWhenNoAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should authenticate request when token is valid and token_version matches DB")
    void shouldAuthenticateWhenTokenVersionMatches() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "valid.jwt.token";

        User user = new User("chef@restaurant.com", "hash", UserRole.CHEF);
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(2);

        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRole(token)).thenReturn(UserRole.CHEF);
        when(jwtService.extractTokenVersion(token)).thenReturn(2);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        User principal = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getEmail()).isEqualTo("chef@restaurant.com");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CHEF");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should authenticate request when token query parameter is provided and header is absent")
    void shouldAuthenticateWhenTokenQueryParameterProvided() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "valid.jwt.param.token";

        User user = new User("comensal@restaurant.com", "hash", UserRole.COMENSAL);
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(1);

        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRole(token)).thenReturn(UserRole.COMENSAL);
        when(jwtService.extractTokenVersion(token)).thenReturn(1);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("token", token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        User principal = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getEmail()).isEqualTo("comensal@restaurant.com");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_COMENSAL");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should reject request with HTTP 401 ProblemDetail when token_version is stale (mismatched)")
    void shouldRejectWhenTokenVersionMismatched() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "stale.jwt.token";

        User user = new User("encargada@restaurant.com", "hash", UserRole.ENCARGADO);
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(3); // DB has version 3

        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRole(token)).thenReturn(UserRole.ENCARGADO);
        when(jwtService.extractTokenVersion(token)).thenReturn(2); // Token has older version 2
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/counter/orders");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");

        JsonNode problem = objectMapper.readTree(response.getContentAsString());
        assertThat(problem.get("status").asInt()).isEqualTo(401);
        assertThat(problem.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(problem.get("detail").asText()).contains("expirado");
    }

    @Test
    @DisplayName("Should reject request with HTTP 401 ProblemDetail when JWT is invalid or expired")
    void shouldRejectWhenJwtInvalid() throws Exception {
        String token = "tampered.or.expired.jwt";

        when(jwtService.validateToken(token)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/users");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");

        JsonNode problem = objectMapper.readTree(response.getContentAsString());
        assertThat(problem.get("status").asInt()).isEqualTo(401);
    }

    @Test
    @DisplayName("Should reject request with HTTP 403 ProblemDetail when user account is suspended")
    void shouldRejectWhenAccountSuspended() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "valid.jwt.token";

        User user = new User("suspended@restaurant.com", "hash", UserRole.COMENSAL);
        user.setId(userId);
        user.setStatus(UserStatus.SUSPENDED);
        user.setTokenVersion(1);

        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRole(token)).thenReturn(UserRole.COMENSAL);
        when(jwtService.extractTokenVersion(token)).thenReturn(1);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/orders");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");

        JsonNode problem = objectMapper.readTree(response.getContentAsString());
        assertThat(problem.get("status").asInt()).isEqualTo(403);
        assertThat(problem.get("title").asText()).isEqualTo("Account Suspended");
    }

    @Test
    @DisplayName("Should allow auth endpoint when user is in PENDING_FIRST_LOGIN status")
    void shouldAllowAuthEndpointWhenUserInPendingFirstLoginStatus() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "temp.jwt.token";

        User user = new User("newbie@restaurant.com", "hash", UserRole.CHEF);
        user.setId(userId);
        user.setStatus(UserStatus.PENDING_FIRST_LOGIN);
        user.setMustChangePassword(true);
        user.setTokenVersion(1);

        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRole(token)).thenReturn(UserRole.CHEF);
        when(jwtService.extractTokenVersion(token)).thenReturn(1);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/first-login-password-change");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should reject operational endpoint with HTTP 403 when user is in PENDING_FIRST_LOGIN status")
    void shouldRejectOperationalEndpointWhenUserInPendingFirstLoginStatus() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "temp.jwt.token";

        User user = new User("newbie@restaurant.com", "hash", UserRole.CHEF);
        user.setId(userId);
        user.setStatus(UserStatus.PENDING_FIRST_LOGIN);
        user.setMustChangePassword(true);
        user.setTokenVersion(1);

        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRole(token)).thenReturn(UserRole.CHEF);
        when(jwtService.extractTokenVersion(token)).thenReturn(1);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/chef/ingredients");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        JsonNode problem = objectMapper.readTree(response.getContentAsString());
        assertThat(problem.get("detail").asText()).contains("Must complete mandatory password change");
    }
}
