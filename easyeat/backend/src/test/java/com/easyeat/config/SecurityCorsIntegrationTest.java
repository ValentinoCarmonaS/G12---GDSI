package com.easyeat.config;

import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityCorsIntegrationTest.TestCorsEndpointsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityCorsIntegrationTest.TestCorsEndpointsController.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=https://easyeat-frontend.onrender.com/, http://localhost:3000"
})
@DisplayName("SecurityCorsIntegrationTest (T011)")
class SecurityCorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @TestConfiguration
    @RestController
    @RequestMapping("/api/v1")
    static class TestCorsEndpointsController {
        @GetMapping("/public-sample")
        public ResponseEntity<String> publicEndpoint() {
            return ResponseEntity.ok("public");
        }

        @PostMapping("/protected-sample")
        public ResponseEntity<String> protectedEndpoint() {
            return ResponseEntity.ok("protected");
        }
    }

    @Test
    @DisplayName("Preflight OPTIONS from allowed origin without trailing slash should return 200 OK and CORS headers")
    void preflightOptionsFromAllowedOrigin_shouldSucceed() throws Exception {
        mockMvc.perform(options("/api/v1/protected-sample")
                        .header(HttpHeaders.ORIGIN, "https://easyeat-frontend.onrender.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://easyeat-frontend.onrender.com"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
    }

    @Test
    @DisplayName("Preflight OPTIONS from localhost:3000 should return 200 OK and CORS headers")
    void preflightOptionsFromLocalhost_shouldSucceed() throws Exception {
        mockMvc.perform(options("/api/v1/public-sample")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
    }

    @Test
    @DisplayName("Preflight OPTIONS from untrusted/unauthorized origin should not receive Access-Control-Allow-Origin header")
    void preflightOptionsFromUntrustedOrigin_shouldNotAllowCors() throws Exception {
        mockMvc.perform(options("/api/v1/public-sample")
                        .header(HttpHeaders.ORIGIN, "https://malicious-site.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
