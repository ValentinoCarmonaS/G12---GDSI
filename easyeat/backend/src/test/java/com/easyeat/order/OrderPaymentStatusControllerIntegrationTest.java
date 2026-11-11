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
import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.config.MercadoPagoProperties;
import com.easyeat.order.controller.OrderPaymentStatusController;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.Payment;
import com.easyeat.order.domain.PaymentStatus;
import com.easyeat.order.domain.ReservationStatus;
import com.easyeat.order.domain.TemporaryReservation;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderPaymentStatusController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
@DisplayName("T005: OrderPaymentStatusControllerIntegrationTest (US1, FR-001, FR-008)")
class OrderPaymentStatusControllerIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-11T18:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private TemporaryReservationRepository temporaryReservationRepository;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private MercadoPagoProperties mercadoPagoProperties;

    @MockBean
    private Clock clock;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private SecurityAuditLogger securityAuditLogger;

    private UUID dinerId;
    private UUID otherUserId;
    private UsernamePasswordAuthenticationToken dinerAuth;

    @BeforeEach
    void setUp() {
        dinerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        User dinerUser = new User();
        dinerUser.setId(dinerId);
        dinerUser.setEmail("diner@easyeat.com");
        dinerUser.setRole(UserRole.COMENSAL);
        dinerUser.setStatus(UserStatus.ACTIVE);

        dinerAuth = new UsernamePasswordAuthenticationToken(
                dinerUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );

        when(clock.instant()).thenReturn(FIXED_NOW);
        when(mercadoPagoProperties.sandbox()).thenReturn(true);
    }

    @Test
    @DisplayName("Retorna 401 Unauthorized si no hay autenticación")
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{orderId}/payment-status", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "chef@easyeat.com", roles = {"CHEF"})
    @DisplayName("Retorna 403 Forbidden si el usuario autenticado no tiene rol COMENSAL")
    void chefRoleReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{orderId}/payment-status", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Retorna 403 Forbidden cuando el comensal autenticado no es dueño de la orden")
    void anotherDinerReturns403Forbidden() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order foreignOrder = buildOrder(orderId, otherUserId, OrderStatus.PENDING_PAYMENT, FIXED_NOW.plusSeconds(300));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(foreignOrder));

        mockMvc.perform(get("/api/v1/orders/{orderId}/payment-status", orderId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(dinerAuth)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Prioriza initPoint sobre sandboxInitPoint y refleja mercadopago.sandbox desde configuración sin findAll()")
    void prioritizesInitPointAndReflectsConfiguredSandbox() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, dinerId, OrderStatus.PENDING_PAYMENT, FIXED_NOW.plusSeconds(600));
        TemporaryReservation reservation = buildReservation(order, FIXED_NOW.plusSeconds(600));
        Payment payment = buildPayment(
                order,
                reservation,
                "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-1",
                "https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-1"
        );

        when(mercadoPagoProperties.sandbox()).thenReturn(false);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(temporaryReservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
        when(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(payment));

        mockMvc.perform(get("/api/v1/orders/{orderId}/payment-status", orderId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(dinerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.payment.redirectUrl").value("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-1"))
                .andExpect(jsonPath("$.payment.sandbox").value(false));

        verify(temporaryReservationRepository).findByOrderId(orderId);
        verify(temporaryReservationRepository, never()).findAll();
    }

    @Test
    @DisplayName("Normaliza cadenas vacías o en blanco a null al resolver redirectUrl")
    void normalizesBlankUrlsToNull() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, dinerId, OrderStatus.PENDING_PAYMENT, FIXED_NOW.plusSeconds(600));
        TemporaryReservation reservation = buildReservation(order, FIXED_NOW.plusSeconds(600));
        Payment payment = buildPayment(order, reservation, "", "   ");

        when(mercadoPagoProperties.sandbox()).thenReturn(true);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(temporaryReservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
        when(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(payment));

        mockMvc.perform(get("/api/v1/orders/{orderId}/payment-status", orderId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(dinerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.redirectUrl").value(nullValue()))
                .andExpect(jsonPath("$.payment.sandbox").value(true));
    }

    @Test
    @DisplayName("Aplica expiración perezosa exacta cuando expiresAt <= now (incluyendo igualdad)")
    void expiresOrderReservationAndPaymentAtExactExpirationBoundary() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, dinerId, OrderStatus.PENDING_PAYMENT, FIXED_NOW);
        TemporaryReservation reservation = buildReservation(order, FIXED_NOW);
        Payment payment = buildPayment(order, reservation, "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-exp", null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(temporaryReservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
        when(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(payment));

        mockMvc.perform(get("/api/v1/orders/{orderId}/payment-status", orderId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(dinerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value(OrderStatus.EXPIRED.name()))
                .andExpect(jsonPath("$.reservation.status").value(ReservationStatus.EXPIRED.name()))
                .andExpect(jsonPath("$.payment.status").value(PaymentStatus.EXPIRED.name()));

        verify(orderRepository).save(order);
        verify(temporaryReservationRepository).save(reservation);
        verify(paymentRepository).save(payment);
    }

    private Order buildOrder(UUID orderId, UUID ownerId, OrderStatus status, Instant expiresAt) {
        return new Order(
                orderId,
                UUID.randomUUID(),
                ownerId,
                FulfillmentMode.IMMEDIATE,
                LocalDate.of(2026, 11, 11),
                LocalTime.of(14, 0),
                LocalTime.of(14, 30),
                status,
                new BigDecimal("4500.00"),
                expiresAt
        );
    }

    private TemporaryReservation buildReservation(Order order, Instant expiresAt) {
        TemporaryReservation reservation = new TemporaryReservation(order, "res-key-" + order.getId(), expiresAt);
        ReflectionTestUtils.setField(reservation, "id", UUID.randomUUID());
        return reservation;
    }

    private Payment buildPayment(Order order, TemporaryReservation reservation, String initPoint, String sandboxInitPoint) {
        Payment payment = new Payment(
                order,
                reservation,
                PaymentMethod.MERCADO_PAGO,
                order.getId().toString(),
                order.getTotalAmount(),
                "pay-key-" + order.getId()
        );
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        payment.assignGatewayPreference("pref-1", initPoint, sandboxInitPoint);
        return payment;
    }
}

