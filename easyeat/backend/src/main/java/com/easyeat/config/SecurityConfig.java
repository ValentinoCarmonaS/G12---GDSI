package com.easyeat.config;

import com.easyeat.auth.security.JwtAuthenticationFilter;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Autowired
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, @Autowired(required = false) ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this(jwtAuthenticationFilter, new ObjectMapper());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.cacheControl(Customizer.withDefaults()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/password-reset/**"
                        ).permitAll()
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
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
