package com.easyeat.catalog;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.catalog.controller.AdminBrandingController;
import com.easyeat.catalog.dto.RestaurantBrandingResponse;
import com.easyeat.catalog.dto.RestaurantBrandingUpdateRequest;
import com.easyeat.catalog.service.BrandingLogoStorageService;
import com.easyeat.catalog.service.RestaurantBrandingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("T041: AdminBrandingIntegrationTest - Admin Branding Endpoints and Tenant Isolation (AS-9, INV-009, CB-009)")
class AdminBrandingIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private RestaurantBrandingService brandingService;

    @Mock
    private BrandingLogoStorageService logoStorageService;

    @InjectMocks
    private AdminBrandingController controller;

    private ObjectMapper objectMapper;
    private UUID myEstablishmentId;
    private UUID otherEstablishmentId;
    private User adminUser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        myEstablishmentId = UUID.randomUUID();
        otherEstablishmentId = UUID.randomUUID();

        adminUser = new User();
        ReflectionTestUtils.setField(adminUser, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(adminUser, "email", "admin@restaurant.com");
        ReflectionTestUtils.setField(adminUser, "role", UserRole.ADMINISTRADOR);
        ReflectionTestUtils.setField(adminUser, "tenantId", myEstablishmentId);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UsernamePasswordAuthenticationToken createAuthToken(User user) {
        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    @Test
    @DisplayName("GET /api/v1/admin/establishments/{id}/branding - returns 200 with branding for authorized tenant")
    void shouldReturnBrandingForAuthorizedAdmin() throws Exception {
        RestaurantBrandingResponse response = new RestaurantBrandingResponse(
                myEstablishmentId,
                "Café Central Palermo",
                "Cafetería de especialidad",
                "/uploads/logos/" + myEstablishmentId + "/logo.png",
                "#059669",
                "#064E3B",
                "#D97706",
                "#FFFFFF",
                "#FFFFFF",
                "#000000",
                Instant.now(),
                false
        );

        when(brandingService.getAdminBranding(myEstablishmentId, adminUser)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/establishments/{establishmentId}/branding", myEstablishmentId)
                        .principal(createAuthToken(adminUser))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fantasyName").value("Café Central Palermo"))
                .andExpect(jsonPath("$.primaryColor").value("#059669"))
                .andExpect(jsonPath("$.primaryContrastColor").value("#FFFFFF"))
                .andExpect(jsonPath("$.accentColor").value("#D97706"))
                .andExpect(jsonPath("$.accentContrastColor").value("#000000"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/establishments/{id}/branding - updates triad and returns calculated contrast colors")
    void shouldUpdateBrandingWithContrastCalculation() throws Exception {
        RestaurantBrandingUpdateRequest request = new RestaurantBrandingUpdateRequest(
                "Café Central Palermo Renovado",
                "Nuevo menú",
                "#2563EB",
                "#1E3A8A",
                "#F59E0B"
        );

        RestaurantBrandingResponse response = new RestaurantBrandingResponse(
                myEstablishmentId,
                "Café Central Palermo Renovado",
                "Nuevo menú",
                "/uploads/logos/" + myEstablishmentId + "/logo.png",
                "#2563EB",
                "#1E3A8A",
                "#F59E0B",
                "#FFFFFF",
                "#FFFFFF",
                "#000000",
                Instant.now(),
                false
        );

        when(brandingService.updateBranding(eq(myEstablishmentId), any(RestaurantBrandingUpdateRequest.class), eq(adminUser)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/establishments/{establishmentId}/branding", myEstablishmentId)
                        .principal(createAuthToken(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fantasyName").value("Café Central Palermo Renovado"))
                .andExpect(jsonPath("$.primaryColor").value("#2563EB"))
                .andExpect(jsonPath("$.primaryContrastColor").value("#FFFFFF"))
                .andExpect(jsonPath("$.accentColor").value("#F59E0B"))
                .andExpect(jsonPath("$.accentContrastColor").value("#000000"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/establishments/{id}/branding/logo - accepts valid PNG and updates logoUrl")
    void shouldUploadValidLogo() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        String savedUrl = "/uploads/logos/" + myEstablishmentId + "/logo.png";
        when(logoStorageService.storeLogo(eq(myEstablishmentId), any(), eq(adminUser)))
                .thenReturn(savedUrl);

        mockMvc.perform(multipart("/api/v1/admin/establishments/{establishmentId}/branding/logo", myEstablishmentId)
                        .file(file)
                        .principal(createAuthToken(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value(savedUrl));
    }
}
