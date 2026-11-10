package com.easyeat.catalog;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.catalog.controller.DinerBrandingController;
import com.easyeat.catalog.dto.RestaurantBrandingResponse;
import com.easyeat.catalog.service.RestaurantBrandingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("T042: DinerBrandingFallbackIntegrationTest - Public Diner Branding and Neutral Fallback (INV-010, Q3/A3, CB-011)")
class DinerBrandingFallbackIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private RestaurantBrandingService brandingService;

    @InjectMocks
    private DinerBrandingController controller;

    private UUID establishmentId;

    @BeforeEach
    void setUp() {
        establishmentId = UUID.randomUUID();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/establishments/{id}/branding - returns custom branding when configured (isDefaultFallback = false)")
    void shouldReturnCustomBrandingWhenPresent() throws Exception {
        RestaurantBrandingResponse custom = new RestaurantBrandingResponse(
                establishmentId,
                "Bistró Gourmet",
                "Comida de autor",
                "/uploads/logos/" + establishmentId + "/logo.png",
                "#2563EB",
                "#1E3A8A",
                "#F59E0B",
                "#FFFFFF",
                "#FFFFFF",
                "#000000",
                Instant.now(),
                false
        );

        when(brandingService.getDinerBranding(establishmentId)).thenReturn(custom);

        mockMvc.perform(get("/api/v1/establishments/{establishmentId}/branding", establishmentId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fantasyName").value("Bistró Gourmet"))
                .andExpect(jsonPath("$.primaryColor").value("#2563EB"))
                .andExpect(jsonPath("$.primaryContrastColor").value("#FFFFFF"))
                .andExpect(jsonPath("$.secondaryColor").value("#1E3A8A"))
                .andExpect(jsonPath("$.accentColor").value("#F59E0B"))
                .andExpect(jsonPath("$.isDefaultFallback").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/establishments/{id}/branding - returns neutral EasyEat fallback when branding not configured (INV-010, CB-011)")
    void shouldReturnNeutralFallbackWhenBrandingNotConfigured() throws Exception {
        RestaurantBrandingResponse fallback = new RestaurantBrandingResponse(
                establishmentId,
                "Restaurante EasyEat",
                "Restaurante asociado a EasyEat",
                "/assets/brand/easyeat-logo-neutral.svg",
                "#10B981",
                "#065F46",
                "#F59E0B",
                "#FFFFFF",
                "#FFFFFF",
                "#000000",
                Instant.now(),
                true
        );

        when(brandingService.getDinerBranding(establishmentId)).thenReturn(fallback);

        mockMvc.perform(get("/api/v1/establishments/{establishmentId}/branding", establishmentId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryColor").value("#10B981"))
                .andExpect(jsonPath("$.secondaryColor").value("#065F46"))
                .andExpect(jsonPath("$.accentColor").value("#F59E0B"))
                .andExpect(jsonPath("$.primaryContrastColor").value("#FFFFFF"))
                .andExpect(jsonPath("$.accentContrastColor").value("#000000"))
                .andExpect(jsonPath("$.isDefaultFallback").value(true));
    }
}
