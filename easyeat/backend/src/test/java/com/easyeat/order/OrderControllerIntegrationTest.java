package com.easyeat.order;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.auth.security.SecurityAuditLogger;
import com.easyeat.config.MethodSecurityConfig;
import com.easyeat.config.SecurityConfig;
import com.easyeat.order.controller.OrderController;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.CreateOrderItemRequest;
import com.easyeat.order.dto.CreateOrderRequest;
import com.easyeat.order.dto.DinerOrderSummaryResponse;
import com.easyeat.order.dto.DinerOrderSummaryResponse.DinerOrderItemResponse;
import com.easyeat.order.dto.OrderCreatedResponse;
import com.easyeat.order.dto.OrderDetailResponse;
import com.easyeat.order.dto.OrderSummaryResponse;
import com.easyeat.order.exception.ActiveReservationExistsException;
import com.easyeat.order.exception.LeadTimeViolationException;
import com.easyeat.order.exception.SlotCapacityExceededException;
import com.easyeat.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
@DisplayName("T043: OrderControllerIntegrationTest MockMvc Integration Tests")
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private SecurityAuditLogger securityAuditLogger;

    private UUID dinerId;
    private User dinerUser;
    private UUID establishmentId;
    private UsernamePasswordAuthenticationToken authPrincipal;

    @BeforeEach
    void setUp() {
        dinerId = UUID.randomUUID();
        dinerUser = new User();
        dinerUser.setId(dinerId);
        dinerUser.setEmail("diner@easyeat.com");
        dinerUser.setRole(UserRole.COMENSAL);
        dinerUser.setStatus(UserStatus.ACTIVE);

        establishmentId = UUID.randomUUID();

        authPrincipal = new UsernamePasswordAuthenticationToken(
                dinerUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );

        when(userRepository.findById(dinerId)).thenReturn(Optional.of(dinerUser));
        when(userRepository.findByEmail("diner@easyeat.com")).thenReturn(Optional.of(dinerUser));
    }

    @Nested
    @DisplayName("Role Security Tests (HTTP 401 & HTTP 403)")
    class RoleSecurityTests {

        @Test
        @DisplayName("POST /api/v1/orders returns 401 Unauthorized for unauthenticated requests")
        void unauthenticatedPostReturns401() throws Exception {
            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.IMMEDIATE,
                    null,
                    null,
                    null,
                    List.of(new CreateOrderItemRequest(UUID.randomUUID(), 1))
            );

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/orders/{id} returns 401 Unauthorized for unauthenticated requests")
        void unauthenticatedGetReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = "chef@easyeat.com", roles = {"CHEF"})
        @DisplayName("POST /api/v1/orders returns 403 Forbidden for unauthorized role (CHEF)")
        void chefRolePostReturns403() throws Exception {
            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.IMMEDIATE,
                    null,
                    null,
                    null,
                    List.of(new CreateOrderItemRequest(UUID.randomUUID(), 1))
            );

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "admin@easyeat.com", roles = {"ADMINISTRADOR"})
        @DisplayName("GET /api/v1/orders/{id} returns 403 Forbidden for unauthorized role (ADMINISTRADOR)")
        void adminRoleGetReturns403() throws Exception {
            mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "counter@easyeat.com", roles = {"MOSTRADOR"})
        @DisplayName("POST /api/v1/orders/{id}/cancel returns 403 Forbidden for unauthorized role (MOSTRADOR)")
        void counterRoleCancelReturns403() throws Exception {
            mockMvc.perform(post("/api/v1/orders/{id}/cancel", UUID.randomUUID()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/v1/orders returns 401 Unauthorized for unauthenticated requests")
        void unauthenticatedGetDinerOrdersReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/orders"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = "admin@easyeat.com", roles = {"ADMINISTRADOR"})
        @DisplayName("GET /api/v1/orders returns 403 Forbidden for unauthorized role (ADMINISTRADOR)")
        void adminRoleGetDinerOrdersReturns403() throws Exception {
            mockMvc.perform(get("/api/v1/orders"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Success Paths (HTTP 201 Created & HTTP 200 OK)")
    class SuccessPathTests {

        @Test
        @DisplayName("POST /api/v1/orders returns 201 Created for IMMEDIATE order")
        void createImmediateOrderReturns201() throws Exception {
            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.IMMEDIATE,
                    null,
                    null,
                    null,
                    List.of(new CreateOrderItemRequest(UUID.randomUUID(), 2))
            );

            OrderCreatedResponse response = new OrderCreatedResponse(
                    UUID.randomUUID(),
                    establishmentId,
                    FulfillmentMode.IMMEDIATE,
                    null,
                    null,
                    null,
                    OrderStatus.PENDING_PAYMENT,
                    new BigDecimal("3500.00"),
                    Instant.now().plusSeconds(900),
                    900L,
                    Instant.now()
            );

            when(orderService.createOrder(eq(dinerId), any(CreateOrderRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/orders")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(response.getId().toString()))
                    .andExpect(jsonPath("$.fulfillmentMode").value("IMMEDIATE"))
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.totalAmount").value(3500.00));
        }

        @Test
        @DisplayName("POST /api/v1/orders returns 201 Created for SCHEDULED order")
        void createScheduledOrderReturns201() throws Exception {
            LocalDate date = LocalDate.now().plusDays(2);
            LocalTime start = LocalTime.of(12, 30);
            LocalTime end = LocalTime.of(12, 45);

            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.SCHEDULED,
                    date,
                    start,
                    end,
                    List.of(new CreateOrderItemRequest(UUID.randomUUID(), 1))
            );

            OrderCreatedResponse response = new OrderCreatedResponse(
                    UUID.randomUUID(),
                    establishmentId,
                    FulfillmentMode.SCHEDULED,
                    date,
                    start,
                    end,
                    OrderStatus.PENDING_PAYMENT,
                    new BigDecimal("4200.00"),
                    Instant.now().plusSeconds(900),
                    900L,
                    Instant.now()
            );

            when(orderService.createOrder(eq(dinerId), any(CreateOrderRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/orders")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(response.getId().toString()))
                    .andExpect(jsonPath("$.fulfillmentMode").value("SCHEDULED"))
                    .andExpect(jsonPath("$.pickupDate").value(date.toString()))
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        }

        @Test
        @DisplayName("GET /api/v1/orders/{id} returns 200 OK with order details")
        void getOrderByIdReturns200() throws Exception {
            UUID orderId = UUID.randomUUID();
            OrderDetailResponse detail = new OrderDetailResponse(
                    orderId,
                    establishmentId,
                    FulfillmentMode.SCHEDULED,
                    LocalDate.now().plusDays(1),
                    LocalTime.of(14, 0),
                    LocalTime.of(14, 15),
                    OrderStatus.PENDING_PAYMENT,
                    new BigDecimal("2800.00"),
                    Instant.now().plusSeconds(720),
                    720L,
                    Instant.now(),
                    List.of()
            );

            when(orderService.getOrderById(eq(orderId), eq(dinerId))).thenReturn(detail);

            mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(orderId.toString()))
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.remainingSeconds").value(720));
        }

        @Test
        @DisplayName("POST /api/v1/orders/{id}/cancel returns 200 OK for voluntary cancellation")
        void cancelOrderReturns200() throws Exception {
            UUID orderId = UUID.randomUUID();
            OrderSummaryResponse summary = new OrderSummaryResponse(
                    orderId,
                    OrderStatus.CANCELLED,
                    Instant.now()
            );

            when(orderService.cancelOrder(eq(orderId), eq(dinerId))).thenReturn(summary);

            mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(orderId.toString()))
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }
    }

    @Nested
    @DisplayName("Error Paths (HTTP 400/422, HTTP 409)")
    class ErrorPathTests {

        @Test
        @DisplayName("POST /api/v1/orders returns 422 Unprocessable Entity for validation error (empty items)")
        void emptyItemsReturns422() throws Exception {
            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.IMMEDIATE,
                    null,
                    null,
                    null,
                    List.of()
            );

            mockMvc.perform(post("/api/v1/orders")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Validation Failed"));
        }

        @Test
        @DisplayName("POST /api/v1/orders returns 422 Unprocessable Entity when establishmentId is null")
        void nullEstablishmentIdReturns422() throws Exception {
            CreateOrderRequest request = new CreateOrderRequest(
                    null,
                    FulfillmentMode.IMMEDIATE,
                    null,
                    null,
                    null,
                    List.of(new CreateOrderItemRequest(UUID.randomUUID(), 1))
            );

            mockMvc.perform(post("/api/v1/orders")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Validation Failed"));
        }

        @Test
        @DisplayName("POST /api/v1/orders returns 409 Conflict when capacity collision occurs (SLOT_SOLD_OUT)")
        void capacityCollisionReturns409() throws Exception {
            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.SCHEDULED,
                    LocalDate.now().plusDays(1),
                    LocalTime.of(13, 0),
                    LocalTime.of(13, 15),
                    List.of(new CreateOrderItemRequest(UUID.randomUUID(), 1))
            );

            when(orderService.createOrder(eq(dinerId), any(CreateOrderRequest.class)))
                    .thenThrow(new SlotCapacityExceededException("La franja horaria seleccionada ya no tiene cupos disponibles."));

            mockMvc.perform(post("/api/v1/orders")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SLOT_SOLD_OUT"))
                    .andExpect(jsonPath("$.detail").value("La franja horaria seleccionada ya no tiene cupos disponibles."));
        }

        @Test
        @DisplayName("POST /api/v1/orders returns 409 Conflict when active reservation exists (ACTIVE_RESERVATION_EXISTS)")
        void activeReservationReturns409() throws Exception {
            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.IMMEDIATE,
                    null,
                    null,
                    null,
                    List.of(new CreateOrderItemRequest(UUID.randomUUID(), 1))
            );

            when(orderService.createOrder(eq(dinerId), any(CreateOrderRequest.class)))
                    .thenThrow(new ActiveReservationExistsException("Ya posee un pedido pendiente de confirmación."));

            mockMvc.perform(post("/api/v1/orders")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ACTIVE_RESERVATION_EXISTS"));
        }

        @Test
        @DisplayName("POST /api/v1/orders returns 422 Unprocessable Entity for lead-time violation (LEAD_TIME_VIOLATION)")
        void leadTimeViolationReturns422() throws Exception {
            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.SCHEDULED,
                    LocalDate.now(),
                    LocalTime.now().plusMinutes(10),
                    LocalTime.now().plusMinutes(25),
                    List.of(new CreateOrderItemRequest(UUID.randomUUID(), 1))
            );

            when(orderService.createOrder(eq(dinerId), any(CreateOrderRequest.class)))
                    .thenThrow(new LeadTimeViolationException("La franja seleccionada no cumple con la anticipación mínima de 30 minutos requerida por la cocina."));

            mockMvc.perform(post("/api/v1/orders")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("LEAD_TIME_VIOLATION"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders Diner Order History Tests")
    class GetDinerOrdersEndpointTests {

        @Test
        @DisplayName("GET /api/v1/orders returns 200 OK with DinerOrderSummaryResponse payload including pending and lazily expired orders")
        void getDinerOrdersReturnsPayloadAndLazilyExpiredStatus() throws Exception {
            UUID confirmedId = UUID.randomUUID();
            UUID pendingId = UUID.randomUUID();
            UUID expiredId = UUID.randomUUID();
            UUID dishId = UUID.randomUUID();
            Instant now = Instant.now();

            DinerOrderSummaryResponse confirmed = new DinerOrderSummaryResponse(
                    confirmedId,
                    108,
                    establishmentId,
                    "La Parrilla de Boedo",
                    OrderStatus.CONFIRMED,
                    FulfillmentMode.SCHEDULED,
                    LocalDate.now().plusDays(1),
                    LocalTime.of(20, 0),
                    LocalTime.of(20, 15),
                    new BigDecimal("18500.00"),
                    now.plusSeconds(600),
                    now.minusSeconds(120),
                    null,
                    "/pedidos/" + confirmedId + "/seguimiento",
                    List.of(new DinerOrderItemResponse(
                            dishId,
                            "Vacío al Asador",
                            1,
                            new BigDecimal("18500.00"),
                            new BigDecimal("18500.00")
                    ))
            );

            DinerOrderSummaryResponse pending = new DinerOrderSummaryResponse(
                    pendingId,
                    null,
                    establishmentId,
                    "La Parrilla de Boedo",
                    OrderStatus.PENDING_PAYMENT,
                    FulfillmentMode.IMMEDIATE,
                    LocalDate.now(),
                    null,
                    null,
                    new BigDecimal("9200.00"),
                    now.plusSeconds(420),
                    now.minusSeconds(300),
                    "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-123",
                    "/pedidos/" + pendingId + "/seguimiento",
                    List.of(new DinerOrderItemResponse(
                            dishId,
                            "Empanadas Salteñas x3",
                            2,
                            new BigDecimal("4600.00"),
                            new BigDecimal("9200.00")
                    ))
            );

            DinerOrderSummaryResponse lazilyExpired = new DinerOrderSummaryResponse(
                    expiredId,
                    null,
                    establishmentId,
                    "La Parrilla de Boedo",
                    OrderStatus.EXPIRED,
                    FulfillmentMode.IMMEDIATE,
                    LocalDate.now(),
                    null,
                    null,
                    new BigDecimal("5400.00"),
                    now.minusSeconds(60),
                    now.minusSeconds(960),
                    null,
                    "/pedidos/" + expiredId + "/seguimiento",
                    List.of()
            );

            when(orderService.getDinerOrders(dinerId))
                    .thenReturn(List.of(confirmed, pending, lazilyExpired));

            mockMvc.perform(get("/api/v1/orders")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].orderId").value(confirmedId.toString()))
                    .andExpect(jsonPath("$[0].ticketNumber").value(108))
                    .andExpect(jsonPath("$[0].establishmentName").value("La Parrilla de Boedo"))
                    .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                    .andExpect(jsonPath("$[0].redirectUrl").value(nullValue()))
                    .andExpect(jsonPath("$[0].trackingUrl").value("/pedidos/" + confirmedId + "/seguimiento"))
                    .andExpect(jsonPath("$[0].items[0].dishName").value("Vacío al Asador"))
                    .andExpect(jsonPath("$[0].items[0].quantity").value(1))
                    .andExpect(jsonPath("$[1].orderId").value(pendingId.toString()))
                    .andExpect(jsonPath("$[1].ticketNumber").value(nullValue()))
                    .andExpect(jsonPath("$[1].status").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$[1].redirectUrl").value("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-123"))
                    .andExpect(jsonPath("$[2].orderId").value(expiredId.toString()))
                    .andExpect(jsonPath("$[2].status").value("EXPIRED"))
                    .andExpect(jsonPath("$[2].redirectUrl").value(nullValue()));

            verify(orderService).getDinerOrders(dinerId);
        }

        @Test
        @DisplayName("GET /api/v1/orders returns 200 OK with empty array when diner has no orders")
        void getDinerOrdersReturnsEmptyArray() throws Exception {
            when(orderService.getDinerOrders(dinerId)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/orders")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(orderService).getDinerOrders(dinerId);
        }
    }
}
