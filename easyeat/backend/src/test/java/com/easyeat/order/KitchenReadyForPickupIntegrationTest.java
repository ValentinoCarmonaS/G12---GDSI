package com.easyeat.order;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.order.controller.KitchenTicketDispatchController;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.KdsTicketResponse;
import com.easyeat.order.exception.OrderNotFoundException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("KitchenReadyForPickupIntegrationTest (AS-8, INV-008, CB-008)")
class KitchenReadyForPickupIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private OrderLiveTrackingService liveTrackingService;

    @InjectMocks
    private KitchenTicketDispatchController controller;

    private ObjectMapper objectMapper;
    private UUID ticketId;
    private UUID orderId;
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

        ticketId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        chefUser = new User();
        chefUser.setId(UUID.randomUUID());
        chefUser.setEmail("carlos.chef@comedorcentral.com");
        chefUser.setRole(UserRole.CHEF);

        auth = new UsernamePasswordAuthenticationToken(
                chefUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CHEF"))
        );
    }

    @Test
    @DisplayName("AS-8/INV-008: POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup marks ticket ready and returns 200 OK")
    void shouldMarkTicketReadyForPickupSuccessfully() throws Exception {
        KdsTicketResponse response = new KdsTicketResponse(
                ticketId,
                orderId,
                142,
                OrderStatus.READY_FOR_PICKUP,
                Instant.now()
        );

        when(liveTrackingService.markTicketReadyForPickup(eq(ticketId), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/kitchen/tickets/{ticketId}/ready-for-pickup", ticketId)
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.ticketNumber").value(142))
                .andExpect(jsonPath("$.status").value("READY_FOR_PICKUP"))
                .andExpect(jsonPath("$.readyAt").exists());
    }

    @Test
    @DisplayName("CB-008: POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup returns 404 if ticket/order not found")
    void shouldReturn404WhenTicketNotFound() throws Exception {
        when(liveTrackingService.markTicketReadyForPickup(eq(ticketId), any()))
                .thenThrow(new OrderNotFoundException(ticketId));

        mockMvc.perform(post("/api/v1/kitchen/tickets/{ticketId}/ready-for-pickup", ticketId)
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
