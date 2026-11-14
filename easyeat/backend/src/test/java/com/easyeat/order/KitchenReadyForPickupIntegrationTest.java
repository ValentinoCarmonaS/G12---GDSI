package com.easyeat.order;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.controller.KitchenTicketDispatchController;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.KdsTicketResponse;
import com.easyeat.order.exception.OrderNotFoundException;
import com.easyeat.order.repository.EstablishmentKdsSettingsRepository;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.service.OrderLiveTrackingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

    @Nested
    @DisplayName("OrderLiveTrackingService.markTicketReadyForPickup vía KdsTicketRepository")
    class MarkTicketReadyForPickupServiceTests {

        @Mock
        private OrderRepository orderRepository;

        @Mock
        private EstablishmentKdsSettingsRepository kdsSettingsRepository;

        @Mock
        private KdsTicketRepository kdsTicketRepository;

        @InjectMocks
        private OrderLiveTrackingService service;

        @Test
        @DisplayName("Resuelve por KdsTicketRepository.findById, actualiza KdsTicket READY_FOR_PICKUP + Order y usa ticketNumber real")
        void shouldResolveByKdsTicketIdSyncStatusAndReturnRealTicketNumber() {
            UUID realTicketId = UUID.randomUUID();
            UUID linkedOrderId = UUID.randomUUID();
            Establishment est = new Establishment();
            est.setId(UUID.randomUUID());

            KdsTicket ticket = new KdsTicket(
                    realTicketId, linkedOrderId, est, 19,
                    KdsTicketStatus.IN_PREPARATION, FulfillmentMode.IMMEDIATE,
                    LocalDate.now(), LocalTime.of(12, 0), LocalTime.of(12, 30), Instant.now()
            );
            Order order = new Order();
            order.setId(linkedOrderId);
            order.setStatus(OrderStatus.IN_PROGRESS);

            when(kdsTicketRepository.findById(realTicketId)).thenReturn(Optional.of(ticket));
            when(orderRepository.findById(linkedOrderId)).thenReturn(Optional.of(order));

            KdsTicketResponse result = service.markTicketReadyForPickup(realTicketId, chefUser.getId());

            assertThat(result.ticketId()).isEqualTo(realTicketId);
            assertThat(result.orderId()).isEqualTo(linkedOrderId);
            assertThat(result.ticketNumber()).isEqualTo(19);
            assertThat(result.status()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
            assertThat(ticket.getStatus()).isEqualTo(KdsTicketStatus.READY_FOR_PICKUP);
            assertThat(ticket.getReadyAt()).isNotNull();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
            verify(kdsTicketRepository).save(ticket);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("Fallback por orderId cuando no existe como ticket.id y sincroniza comanda asociada si existe")
        void shouldFallbackToOrderIdAndSyncAssociatedTicket() {
            UUID linkedOrderId = UUID.randomUUID();
            UUID realTicketId = UUID.randomUUID();
            Establishment est = new Establishment();
            est.setId(UUID.randomUUID());

            Order order = new Order();
            order.setId(linkedOrderId);
            order.setStatus(OrderStatus.IN_PROGRESS);

            KdsTicket ticket = new KdsTicket(
                    realTicketId, linkedOrderId, est, 33,
                    KdsTicketStatus.QUEUED, FulfillmentMode.IMMEDIATE,
                    LocalDate.now(), LocalTime.of(12, 0), LocalTime.of(12, 30), Instant.now()
            );

            when(kdsTicketRepository.findById(linkedOrderId)).thenReturn(Optional.empty());
            when(orderRepository.findById(linkedOrderId)).thenReturn(Optional.of(order));
            when(kdsTicketRepository.findByOrderId(linkedOrderId)).thenReturn(Optional.of(ticket));

            KdsTicketResponse result = service.markTicketReadyForPickup(linkedOrderId, chefUser.getId());

            assertThat(result.ticketId()).isEqualTo(realTicketId);
            assertThat(result.orderId()).isEqualTo(linkedOrderId);
            assertThat(result.ticketNumber()).isEqualTo(33);
            assertThat(ticket.getStatus()).isEqualTo(KdsTicketStatus.READY_FOR_PICKUP);
            assertThat(ticket.getReadyAt()).isNotNull();
            verify(kdsTicketRepository).save(ticket);
        }
    }
}
