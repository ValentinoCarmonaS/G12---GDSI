package com.easyeat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityCorsConfigurationTest (T010)")
class SecurityCorsConfigurationTest {

    @Test
    @DisplayName("Should parse and sanitize comma separated origins, trim spaces and remove trailing slashes")
    void shouldSanitizeOriginsWithTrimAndRemoveTrailingSlashes() {
        SecurityConfig config = new SecurityConfig(null);
        String rawOrigins = " https://easyeat-frontend.onrender.com/ , http://localhost:3000/// , http://localhost:5173 ";

        CorsConfigurationSource source = config.corsConfigurationSource(rawOrigins);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/establishments");
        CorsConfiguration corsConfig = source.getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        List<String> allowedOrigins = corsConfig.getAllowedOrigins();
        assertThat(allowedOrigins).containsExactly(
                "https://easyeat-frontend.onrender.com",
                "http://localhost:3000",
                "http://localhost:5173"
        );
    }

    @Test
    @DisplayName("Should fallback to http://localhost:3000 when origins property is null, empty or only whitespace/slashes")
    void shouldFallbackToLocalhost3000WhenOriginsEmptyOrBlank() {
        SecurityConfig config = new SecurityConfig(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");

        CorsConfiguration corsNull = config.corsConfigurationSource(null).getCorsConfiguration(request);
        assertThat(corsNull).isNotNull();
        assertThat(corsNull.getAllowedOrigins()).containsExactly("http://localhost:3000");

        CorsConfiguration corsEmpty = config.corsConfigurationSource("").getCorsConfiguration(request);
        assertThat(corsEmpty).isNotNull();
        assertThat(corsEmpty.getAllowedOrigins()).containsExactly("http://localhost:3000");

        CorsConfiguration corsWhitespace = config.corsConfigurationSource("   , /// ,  ").getCorsConfiguration(request);
        assertThat(corsWhitespace).isNotNull();
        assertThat(corsWhitespace.getAllowedOrigins()).containsExactly("http://localhost:3000");
    }

    @Test
    @DisplayName("Should configure required HTTP methods, wildcard headers, allowCredentials true and maxAge 3600")
    void shouldConfigureStandardCorsParameters() {
        SecurityConfig config = new SecurityConfig(null);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/orders");
        CorsConfiguration cors = config.corsConfigurationSource("http://localhost:3000").getCorsConfiguration(request);

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).containsExactly("*");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getMaxAge()).isEqualTo(3600L);
    }
}
