package com.easyeat.auth.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.dto.FirstLoginPasswordChangeRequest;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FirstLoginPasswordController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
class FirstLoginPasswordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /api/v1/auth/first-login-password-change - permits unauthenticated request and updates password")
    void shouldAllowUnauthenticatedFirstLoginPasswordChange() throws Exception {
        String email = "chef@easyeat.com";
        String currentTemp = "TempPass123!";
        String newPass = "NewPermanentPass123!";

        User user = new User(email, "hashed_temp", UserRole.CHEF);
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.PENDING_FIRST_LOGIN);
        user.setMustChangePassword(true);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(currentTemp, "hashed_temp")).thenReturn(true);
        when(passwordEncoder.encode(newPass)).thenReturn("hashed_new");

        FirstLoginPasswordChangeRequest request = new FirstLoginPasswordChangeRequest(
                email, currentTemp, newPass, newPass
        );

        mockMvc.perform(post("/api/v1/auth/first-login-password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully. Account is now active."));
    }

    @Test
    @DisplayName("POST /api/v1/auth/first-login-password-change - 400 when new passwords do not match")
    void shouldReturn400WhenPasswordsMismatch() throws Exception {
        FirstLoginPasswordChangeRequest request = new FirstLoginPasswordChangeRequest(
                "chef@easyeat.com", "TempPass123!", "NewPass123!", "MismatchPass123!"
        );

        mockMvc.perform(post("/api/v1/auth/first-login-password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
