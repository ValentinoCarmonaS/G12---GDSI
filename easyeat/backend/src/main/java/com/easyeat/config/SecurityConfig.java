package com.easyeat.config;

import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.SecurityAuditLogger;
import com.easyeat.common.filter.TenantContextFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Optional;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final TenantContextFilter tenantContextFilter;
    private final SecurityAuditLogger securityAuditLogger;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            Optional<TenantContextFilter> tenantContextFilter,
            Optional<SecurityAuditLogger> securityAuditLogger) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.tenantContextFilter = tenantContextFilter.orElse(null);
        this.securityAuditLogger = securityAuditLogger.orElse(null);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "ADMINISTRADOR")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/problem+json");
                            response.getWriter().write("{\"type\":\"https://easyeat.com/errors/unauthorized\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Authentication is required to access this resource.\",\"instance\":\"" + request.getRequestURI() + "\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (securityAuditLogger != null) {
                                String userEmail = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";
                                securityAuditLogger.logAccessDenied(userEmail, null, request.getRequestURI(), request.getRemoteAddr(), accessDeniedException.getMessage());
                            }
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/problem+json");
                            response.getWriter().write("{\"type\":\"https://easyeat.com/errors/forbidden\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"Access denied: insufficient operational role permissions.\",\"instance\":\"" + request.getRequestURI() + "\"}");
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (tenantContextFilter != null) {
            http.addFilterAfter(tenantContextFilter, JwtAuthenticationFilter.class);
        }

        return http.build();
    }
}
