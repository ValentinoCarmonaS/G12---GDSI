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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WebMvcStaticResourceTest.DummyController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class, WebMvcStaticResourceTest.DummyController.class})
@DisplayName("WebMvcConfig Static Resource Serving and Security Tests (AS-9, INV-009)")
class WebMvcStaticResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @TestConfiguration
    @RestController
    static class DummyController {
        @GetMapping("/api/test-dummy")
        public String ping() {
            return "pong";
        }
    }

    @Test
    @DisplayName("GET /uploads/** is public and serves static logo file with public cache header")
    void shouldServeUploadedLogoWithPublicCacheHeader() throws Exception {
        mockMvc.perform(get("/uploads/logos/1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563/logo.png"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=86400, public"));
    }

    @Test
    @DisplayName("GET /uploads/non-existent.png returns 404 without authentication challenge (401)")
    void shouldReturn404ForMissingUploadWithoutAuth() throws Exception {
        mockMvc.perform(get("/uploads/logos/non-existent/missing.png"))
                .andExpect(status().isNotFound());
    }
}
