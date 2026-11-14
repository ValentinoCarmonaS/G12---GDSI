package com.easyeat.catalog.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.catalog.domain.RestaurantLogo;
import com.easyeat.catalog.service.BrandingLogoStorageService;
import com.easyeat.catalog.service.RestaurantBrandingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("DinerLogoEndpointTest - Public Restaurant Logo Endpoint Tests (INV-009, CB-010)")
class DinerLogoEndpointTest {

    private MockMvc mockMvc;

    @Mock
    private RestaurantBrandingService brandingService;

    @Mock
    private BrandingLogoStorageService logoStorageService;

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
    @DisplayName("GET /api/v1/establishments/{id}/logo returns 200 with image bytes and public cache header")
    void shouldReturnLogoBytesWhenPresent() throws Exception {
        byte[] imageBytes = new byte[]{1, 2, 3, 4};
        RestaurantLogo logo = new RestaurantLogo(establishmentId, "logo.png", "image/png", 4L, imageBytes);

        when(logoStorageService.getLogo(establishmentId)).thenReturn(Optional.of(logo));

        mockMvc.perform(get("/api/v1/establishments/{id}/logo", establishmentId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=86400"))
                .andExpect(content().bytes(imageBytes));
    }

    @Test
    @DisplayName("GET /api/v1/establishments/{id}/logo returns 404 when logo not uploaded")
    void shouldReturn404WhenLogoMissing() throws Exception {
        when(logoStorageService.getLogo(establishmentId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/establishments/{id}/logo", establishmentId))
                .andExpect(status().isNotFound());
    }
}
