package com.easyeat.config;

import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.SecurityAuditLogger;
import com.easyeat.common.filter.TenantContextFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.net.URI;
import java.util.Optional;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final TenantContextFilter tenantContextFilter;
    private final SecurityAuditLogger securityAuditLogger;
    private final ObjectMapper objectMapper;

    @Autowired
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            Optional<TenantContextFilter> tenantContextFilter,
            Optional<SecurityAuditLogger> securityAuditLogger,
            Optional<ObjectMapper> objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.tenantContextFilter = tenantContextFilter.orElse(null);
        this.securityAuditLogger = securityAuditLogger.orElse(null);
        this.objectMapper = objectMapper.orElseGet(ObjectMapper::new);
    }

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this(jwtAuthenticationFilter, Optional.empty(), Optional.empty(), Optional.ofNullable(objectMapper));
    }

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this(jwtAuthenticationFilter, Optional.empty(), Optional.empty(), Optional.empty());
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
                .headers(headers -> headers.cacheControl(Customizer.withDefaults()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/password-reset/**",
                                "/api/v1/auth/first-login-password-change",
                                "/api/v1/dietary-restrictions",
                                "/api/v1/profile/email-change/confirm",
                                "/api/v1/establishments/*/dishes",
                                "/api/v1/establishments/{tenantId}/dishes"
                        ).permitAll()
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/dishes/**").hasRole("CHEF")
                        .requestMatchers("/api/v1/admin/notifications/**").hasAnyRole("ADMINISTRADOR", "ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMINISTRADOR", "ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/problem+json;charset=UTF-8");
                            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                                     HttpStatus.UNAUTHORIZED,
                                    "Se requiere autenticación o la sesión ha expirado"
                            );
                            problem.setType(URI.create("about:blank"));
                            problem.setTitle("Unauthorized");
                            problem.setInstance(URI.create(request.getRequestURI()));
                            objectMapper.writeValue(response.getWriter(), problem);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (securityAuditLogger != null) {
                                String userEmail = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";
                                securityAuditLogger.logAccessDenied(userEmail, null, request.getRequestURI(), request.getRemoteAddr(), accessDeniedException.getMessage());
                            }
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/problem+json;charset=UTF-8");
                            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.FORBIDDEN,
                                    "Access denied: insufficient operational role permissions."
                            );
                            problem.setType(URI.create("https://easyeat.com/errors/forbidden"));
                            problem.setTitle("Forbidden");
                            problem.setInstance(URI.create(request.getRequestURI()));
                            objectMapper.writeValue(response.getWriter(), problem);
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (tenantContextFilter != null) {
            http.addFilterAfter(tenantContextFilter, JwtAuthenticationFilter.class);
        }

        return http.build();
    }
}
