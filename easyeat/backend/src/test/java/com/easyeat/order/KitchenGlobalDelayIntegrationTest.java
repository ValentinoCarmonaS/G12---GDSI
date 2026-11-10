package com.easyeat.order;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.User;
import com.easyeat.order.controller.KitchenTicketDispatchController;
import com.easyeat.order.dto.KdsDelayAdjustmentRequest;
import com.easyeat.order.dto.KdsDelayAdjustmentResponse;
import com.easyeat.order.service.OrderLiveTrackingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("KitchenGlobalDelayIntegrationTest (AS-7, INV-007, Q6/A6, CB-007)")
class KitchenGlobalDelayIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private OrderLiveTrackingService liveTrackingService;

    @InjectMocks
    private KitchenTicketDispatchController controller;

    private ObjectMapper objectMapper;
    private UUID establishmentId;
    private User chefUser;
    private UsernamePasswordAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();

        establishmentId = UUID.randomUUID();
        chefUser = new User();
        chefUser.setId(UUID.randomUUID());
        chefUser.setEmail("carlos.chef@comedorcentral.com");
        chefUser.setRole(UserRole.CHEF);
        chefUser.setTenantId(establishmentId);

        auth = new UsernamePasswordAuthenticationToken(
                chefUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CHEF"))
        );
    }

    @Test
    @DisplayName("AS-7/INV-007: PUT /api/v1/kitchen/delay saves global delay and returns 200 OK")
    void shouldUpdateKitchenGlobalDelaySuccessfully() throws Exception {
        KdsDelayAdjustmentRequest request = new KdsDelayAdjustmentRequest(establishmentId, 10);
        KdsDelayAdjustmentResponse response = new KdsDelayAdjustmentResponse(
                establishmentId,
                10,
                Instant.now()
        );

        when(liveTrackingService.updateGlobalDelay(any(KdsDelayAdjustmentRequest.class), any()))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/kitchen/delay")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishmentId").value(establishmentId.toString()))
                .andExpect(jsonPath("$.globalDelayMinutes").value(10))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("CB-007: PUT /api/v1/kitchen/delay rejects negative delay values with 400 Bad Request")
    void shouldRejectNegativeGlobalDelayValues() throws Exception {
        String invalidJson = """
            {
                "establishmentId": "%s",
                "globalDelayMinutes": -5
            }
            """.formatted(establishmentId);

        mockMvc.perform(put("/api/v1/kitchen/delay")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isUnprocessableEntity());
    }
}
