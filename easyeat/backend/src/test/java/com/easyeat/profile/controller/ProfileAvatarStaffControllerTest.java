package com.easyeat.profile.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.profile.domain.UserPhoto;
import com.easyeat.profile.service.UserPhotoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileAvatarStaffControllerTest (T025, US4, ENC-2.1.2, RF-012)")
class ProfileAvatarStaffControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserPhotoService userPhotoService;

    private ProfileAvatarController controller;
    private UsernamePasswordAuthenticationToken encargadoAuth;

    @BeforeEach
    void setUp() {
        controller = new ProfileAvatarController(userPhotoService);

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();

        User encargado = new User();
        encargado.setId(UUID.randomUUID());
        encargado.setEmail("encargado@easyeat.com");
        encargado.setRole(UserRole.ENCARGADO);

        encargadoAuth = new UsernamePasswordAuthenticationToken(
                encargado,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ENCARGADO"))
        );
    }

    @Test
    @DisplayName("T025/T029: GET /api/v1/profile/users/{userId}/avatar declares @PreAuthorize for ENCARGADO, CHEF and ADMINISTRADOR")
    void shouldDeclarePreAuthorizeForStaffRoles() throws Exception {
        Method method = ProfileAvatarController.class.getMethod("getUserAvatarById", UUID.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value())
                .contains("ENCARGADO")
                .contains("CHEF")
                .contains("ADMINISTRADOR");
    }

    @Test
    @DisplayName("T025: GET /api/v1/profile/users/{userId}/avatar with role ENCARGADO returns 200 binary stream when user has avatar")
    void shouldReturnBinaryStreamWhenUserHasAvatar() throws Exception {
        UUID dinerId = UUID.fromString("c71e2289-4b68-45e0-a7d1-e6e7d6928e01");
        byte[] imageBytes = "fake-jpeg-binary-stream".getBytes(StandardCharsets.UTF_8);
        UserPhoto photo = new UserPhoto(dinerId, "avatar.jpg", "image/jpeg", (long) imageBytes.length, imageBytes);

        when(userPhotoService.getPhoto(dinerId)).thenReturn(Optional.of(photo));

        mockMvc.perform(get("/api/v1/profile/users/{userId}/avatar", dinerId)
                        .principal(encargadoAuth))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=3600"))
                .andExpect(content().bytes(imageBytes));
    }

    @Test
    @DisplayName("T025: GET /api/v1/profile/users/{userId}/avatar with role ENCARGADO returns 404 ProblemDetail when user has no avatar")
    void shouldReturn404WhenUserHasNoAvatar() throws Exception {
        UUID dinerId = UUID.fromString("d98f1214-9271-4899-8ab3-772c21980a34");

        when(userPhotoService.getPhoto(dinerId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/profile/users/{userId}/avatar", dinerId)
                        .principal(encargadoAuth)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/avatar-not-found"))
                .andExpect(jsonPath("$.title").value("Avatar Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No custom avatar configured for user"));
    }
}
