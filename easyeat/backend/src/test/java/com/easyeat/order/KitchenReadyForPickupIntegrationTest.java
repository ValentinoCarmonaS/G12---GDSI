package com.easyeat.order;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.order.controller.KitchenTicketDispatchController;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.KdsTicketResponse;
import com.easyeat.order.exception.InvalidOrderStateException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("KitchenReadyForPickupIntegrationTest (T008, US1, CHF-4.1.3, RF-001..RF-004)")
class KitchenReadyForPickupIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private OrderLiveTrackingService liveTrackingService;

    private KitchenTicketDispatchController controller;

    private ObjectMapper objectMapper;
    private UUID ticketId;
    private UUID orderId;
    private User chefUser;
    private UsernamePasswordAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        controller = new KitchenTicketDispatchController(liveTrackingService);
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
    @DisplayName("T008/T012: POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup restricts access to CHEF, ENCARGADO and ADMINISTRADOR")
    void shouldDeclarePreAuthorizeForChefEncargadoAndAdministrador() throws Exception {
        Method method = KitchenTicketDispatchController.class.getMethod(
                "markTicketReadyForPickup",
                UUID.class,
                User.class
        );
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value())
                .contains("CHEF")
                .contains("ENCARGADO")
                .contains("ADMINISTRADOR");
    }

    @Test
    @DisplayName("T008: POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup marks ticket ready and returns 200 OK")
    void shouldMarkTicketReadyForPickupSuccessfully() throws Exception {
        Instant readyAt = Instant.parse("2026-11-15T19:30:00Z");
        KdsTicketResponse response = new KdsTicketResponse(
                ticketId,
                orderId,
                104,
                OrderStatus.READY_FOR_PICKUP,
                readyAt
        );

        when(liveTrackingService.markTicketReadyForPickup(eq(ticketId), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/kitchen/tickets/{ticketId}/ready-for-pickup", ticketId)
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.ticketNumber").value(104))
                .andExpect(jsonPath("$.status").value("READY_FOR_PICKUP"))
                .andExpect(jsonPath("$.readyAt").exists());
    }

    @Test
    @DisplayName("T008: POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup returns 404 Not Found if ticket does not exist")
    void shouldReturn404WhenTicketNotFound() throws Exception {
        when(liveTrackingService.markTicketReadyForPickup(eq(ticketId), any()))
                .thenThrow(new OrderNotFoundException(ticketId));

        mockMvc.perform(post("/api/v1/kitchen/tickets/{ticketId}/ready-for-pickup", ticketId)
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("T008: POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup returns 403 Forbidden if ticket belongs to another establishment")
    void shouldReturn403WhenTicketBelongsToDifferentEstablishment() throws Exception {
        when(liveTrackingService.markTicketReadyForPickup(eq(ticketId), any()))
                .thenThrow(new TenantViolationException("La comanda no pertenece al establecimiento del usuario autenticado."));

        mockMvc.perform(post("/api/v1/kitchen/tickets/{ticketId}/ready-for-pickup", ticketId)
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("La comanda no pertenece al establecimiento del usuario autenticado."));
    }

    @Test
    @DisplayName("T008: POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup returns 409 Conflict with ORDER_ALREADY_COMPLETED when ticket is COMPLETED")
    void shouldReturn409WhenTicketAlreadyCompleted() throws Exception {
        OrderLiveTrackingService realServiceWithMocks;
        OrderRepository orderRepo = org.mockito.Mockito.mock(OrderRepository.class);
        EstablishmentKdsSettingsRepository kdsSettingsRepo = org.mockito.Mockito.mock(EstablishmentKdsSettingsRepository.class);
        KdsTicketRepository kdsTicketRepo = org.mockito.Mockito.mock(KdsTicketRepository.class);

        Establishment est = new Establishment();
        est.setId(UUID.randomUUID());
        KdsTicket completedTicket = new KdsTicket(
                ticketId, orderId, est, 104,
                KdsTicketStatus.COMPLETED, FulfillmentMode.IMMEDIATE,
                LocalDate.now(), LocalTime.of(13, 0), LocalTime.of(13, 15), Instant.now()
        );
        when(kdsTicketRepo.findByIdForUpdate(ticketId)).thenReturn(Optional.of(completedTicket));

        realServiceWithMocks = new OrderLiveTrackingService(
                orderRepo,
                kdsSettingsRepo,
                null,
                Clock.fixed(Instant.parse("2026-11-15T19:30:00Z"), ZoneOffset.UTC),
                kdsTicketRepo
        );

        MockMvc realServiceMockMvc = MockMvcBuilders.standaloneSetup(new KitchenTicketDispatchController(realServiceWithMocks))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        realServiceMockMvc.perform(post("/api/v1/kitchen/tickets/{ticketId}/ready-for-pickup", ticketId)
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_COMPLETED"))
                .andExpect(jsonPath("$.detail").value("La orden ya ha sido completada y entregada al cliente."));
    }

    @Nested
    @DisplayName("OrderLiveTrackingService.markTicketReadyForPickup — Transición atómica, coincidencia temporal y validaciones (T008, T011)")
    class MarkTicketReadyForPickupServiceTests {

        @Mock
        private OrderRepository orderRepository;

        @Mock
        private EstablishmentKdsSettingsRepository kdsSettingsRepository;

        @Mock
        private KdsTicketRepository kdsTicketRepository;

        @Mock
        private UserRepository userRepository;

        private Clock fixedClock;
        private OrderLiveTrackingService service;

        @BeforeEach
        void setUpService() {
            fixedClock = Clock.fixed(Instant.parse("2026-11-15T19:30:00.123456Z"), ZoneOffset.UTC);
            service = new OrderLiveTrackingService(
                    orderRepository,
                    kdsSettingsRepository,
                    null,
                    fixedClock,
                    kdsTicketRepository
            );
        }

        @Test
        @DisplayName("AC-2 / RF-003 / Invariante 1 y 2: Bloquea con findByIdForUpdate y actualiza KdsTicket y Order con idéntico readyAt")
        void shouldLockTicketWithFindByIdForUpdateAndAtomicallySetIdenticalReadyAtOnTicketAndOrder() {
            UUID realTicketId = UUID.randomUUID();
            UUID linkedOrderId = UUID.randomUUID();
            UUID establishmentId = UUID.randomUUID();
            Establishment est = new Establishment();
            est.setId(establishmentId);

            KdsTicket ticket = new KdsTicket(
                    realTicketId, linkedOrderId, est, 104,
                    KdsTicketStatus.IN_PREPARATION, FulfillmentMode.IMMEDIATE,
                    LocalDate.of(2026, 11, 15), LocalTime.of(13, 0), LocalTime.of(13, 15),
                    Instant.parse("2026-11-15T19:15:00Z")
            );
            Order order = new Order();
            order.setId(linkedOrderId);
            order.setEstablishmentId(establishmentId);
            order.setStatus(OrderStatus.IN_PROGRESS);

            when(kdsTicketRepository.findByIdForUpdate(realTicketId)).thenReturn(Optional.of(ticket));
            when(orderRepository.findByIdForUpdate(linkedOrderId)).thenReturn(Optional.of(order));

            KdsTicketResponse result = service.markTicketReadyForPickup(realTicketId, chefUser.getId());

            Instant expectedReadyAt = fixedClock.instant();
            assertThat(result.ticketId()).isEqualTo(realTicketId);
            assertThat(result.orderId()).isEqualTo(linkedOrderId);
            assertThat(result.ticketNumber()).isEqualTo(104);
            assertThat(result.status()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
            assertThat(result.readyAt()).isEqualTo(expectedReadyAt);

            // Invariante 1 & 2: both entities transition to READY_FOR_PICKUP with identical readyAt
            assertThat(ticket.getStatus()).isEqualTo(KdsTicketStatus.READY_FOR_PICKUP);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
            assertThat(ticket.getReadyAt()).isEqualTo(expectedReadyAt);
            assertThat(order.getReadyAt()).isEqualTo(expectedReadyAt);
            assertThat(order.getReadyAt()).isEqualTo(ticket.getReadyAt());

            verify(kdsTicketRepository).findByIdForUpdate(realTicketId);
            verify(kdsTicketRepository).save(ticket);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("Fallback por orderId sincroniza Order y KdsTicket con idéntico readyAt")
        void shouldFallbackToOrderIdAndSyncAssociatedTicketWithIdenticalReadyAt() {
            UUID linkedOrderId = UUID.randomUUID();
            UUID realTicketId = UUID.randomUUID();
            Establishment est = new Establishment();
            est.setId(UUID.randomUUID());

            Order order = new Order();
            order.setId(linkedOrderId);
            order.setEstablishmentId(est.getId());
            order.setStatus(OrderStatus.IN_PROGRESS);

            KdsTicket ticket = new KdsTicket(
                    realTicketId, linkedOrderId, est, 33,
                    KdsTicketStatus.QUEUED, FulfillmentMode.IMMEDIATE,
                    LocalDate.of(2026, 11, 15), LocalTime.of(12, 0), LocalTime.of(12, 30),
                    Instant.parse("2026-11-15T19:10:00Z")
            );

            when(kdsTicketRepository.findByIdForUpdate(linkedOrderId)).thenReturn(Optional.empty());
            when(orderRepository.findByIdForUpdate(linkedOrderId)).thenReturn(Optional.of(order));
            when(kdsTicketRepository.findByOrderId(linkedOrderId)).thenReturn(Optional.of(ticket));

            KdsTicketResponse result = service.markTicketReadyForPickup(linkedOrderId, chefUser.getId());

            Instant expectedReadyAt = fixedClock.instant();
            assertThat(result.ticketId()).isEqualTo(realTicketId);
            assertThat(result.orderId()).isEqualTo(linkedOrderId);
            assertThat(result.ticketNumber()).isEqualTo(33);
            assertThat(ticket.getStatus()).isEqualTo(KdsTicketStatus.READY_FOR_PICKUP);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
            assertThat(ticket.getReadyAt()).isEqualTo(expectedReadyAt);
            assertThat(order.getReadyAt()).isEqualTo(expectedReadyAt);
            verify(kdsTicketRepository).save(ticket);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("Contrato 01 (409 Conflict): Lanza InvalidOrderStateException cuando el ticket ya está en estado COMPLETED")
        void shouldThrowInvalidOrderStateExceptionWhenTicketAlreadyCompleted() {
            UUID realTicketId = UUID.randomUUID();
            UUID linkedOrderId = UUID.randomUUID();
            Establishment est = new Establishment();
            est.setId(UUID.randomUUID());

            KdsTicket completedTicket = new KdsTicket(
                    realTicketId, linkedOrderId, est, 104,
                    KdsTicketStatus.COMPLETED, FulfillmentMode.IMMEDIATE,
                    LocalDate.now(), LocalTime.of(13, 0), LocalTime.of(13, 15), Instant.now()
            );

            when(kdsTicketRepository.findByIdForUpdate(realTicketId)).thenReturn(Optional.of(completedTicket));

            assertThatThrownBy(() -> service.markTicketReadyForPickup(realTicketId, chefUser.getId()))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessage("La orden ya ha sido completada y entregada al cliente.");
        }

        @Test
        @DisplayName("Contrato 01 (403 Forbidden): Lanza TenantViolationException cuando el ticket pertenece a otro establecimiento")
        void shouldThrowTenantViolationExceptionWhenTicketBelongsToDifferentEstablishment() {
            UUID realTicketId = UUID.randomUUID();
            UUID linkedOrderId = UUID.randomUUID();
            Establishment ticketEst = new Establishment();
            ticketEst.setId(UUID.randomUUID());

            User foreignChef = new User();
            foreignChef.setId(UUID.randomUUID());
            foreignChef.setRole(UserRole.CHEF);
            foreignChef.setTenantId(UUID.randomUUID());

            KdsTicket ticket = new KdsTicket(
                    realTicketId, linkedOrderId, ticketEst, 104,
                    KdsTicketStatus.IN_PREPARATION, FulfillmentMode.IMMEDIATE,
                    LocalDate.now(), LocalTime.of(13, 0), LocalTime.of(13, 15), Instant.now()
            );

            org.springframework.test.util.ReflectionTestUtils.setField(service, "userRepository", userRepository);
            when(kdsTicketRepository.findByIdForUpdate(realTicketId)).thenReturn(Optional.of(ticket));
            when(userRepository.findById(foreignChef.getId())).thenReturn(Optional.of(foreignChef));

            assertThatThrownBy(() -> service.markTicketReadyForPickup(realTicketId, foreignChef.getId()))
                    .isInstanceOf(TenantViolationException.class)
                    .hasMessage("La comanda no pertenece al establecimiento del usuario autenticado.");
        }
    }
}
