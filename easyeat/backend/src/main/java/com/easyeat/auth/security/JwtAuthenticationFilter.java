package com.easyeat.auth.security;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this(jwtService, userRepository, new ObjectMapper());
    }

    @Override
    public void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (request.getParameter("token") != null && !request.getParameter("token").isBlank()) {
            token = request.getParameter("token");
        }

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtService.validateToken(token)) {
            sendProblemDetail(response, request.getRequestURI(), HttpStatus.UNAUTHORIZED, "Unauthorized", "Tu sesión ha expirado o el token es inválido");
            return;
        }

        try {
            UUID userId = jwtService.extractUserId(token);
            UserRole role = jwtService.extractRole(token);
            Integer tokenVersion = jwtService.extractTokenVersion(token);

            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isEmpty()) {
                sendProblemDetail(response, request.getRequestURI(), HttpStatus.UNAUTHORIZED, "Unauthorized", "Usuario no encontrado");
                return;
            }

            User user = userOpt.get();

            if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.PENDING_FIRST_LOGIN) {
                sendProblemDetail(response, request.getRequestURI(), HttpStatus.FORBIDDEN, "Account Suspended", "La cuenta se encuentra inactiva o suspendida");
                return;
            }

            if (user.getStatus() == UserStatus.PENDING_FIRST_LOGIN || Boolean.TRUE.equals(user.getMustChangePassword())) {
                String uri = request.getRequestURI();
                boolean isAllowedAuthEndpoint = uri.startsWith("/api/v1/auth/first-login-password-change")
                        || uri.startsWith("/api/v1/auth/logout")
                        || uri.startsWith("/api/v1/auth/me");
                if (!isAllowedAuthEndpoint) {
                    sendProblemDetail(response, request.getRequestURI(), HttpStatus.FORBIDDEN, "Forbidden", "Must complete mandatory password change before accessing platform functionality");
                    return;
                }
            }

            // Single active session verification: token_version must match persisted user version
            if (tokenVersion == null || !user.getTokenVersion().equals(tokenVersion)) {
                sendProblemDetail(response, request.getRequestURI(), HttpStatus.UNAUTHORIZED, "Unauthorized", "Tu sesión ha expirado o ha sido invalidada");
                return;
            }

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
            var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            sendProblemDetail(response, request.getRequestURI(), HttpStatus.UNAUTHORIZED, "Unauthorized", "Tu sesión ha expirado o el token es inválido");
        }
    }

    private void sendProblemDetail(HttpServletResponse response, String instanceUri, HttpStatus status, String title, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json;charset=UTF-8");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(title);
        problem.setInstance(URI.create(instanceUri));
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
