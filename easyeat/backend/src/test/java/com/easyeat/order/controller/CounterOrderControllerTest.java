package com.easyeat.order.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.CompleteDeliveryResponse;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.exception.OrderNotFoundException;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.service.CounterOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 022, T019 (US2, contract 01): web layer of {@code POST /api/v1/counter/orders/{orderId}/complete-delivery}
 * — authorization declaration and the mapping of each service outcome to its HTTP status and problem code.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CounterOrderControllerTest (T019, spec 022, US2, contract 01)")
class CounterOrderControllerTest {

    private static final Instant COMPLETED_AT = Instant.parse("2026-11-15T18:30:00Z");

    @Mock
    private KdsTicketRepository kdsTicketRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CounterOrderService counterOrderService;

    private MockMvc mockMvc;
    private User encargado;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        CounterOrderController controller = new CounterOrderController(
                kdsTicketRepository, orderRepository, null, null, Clock.systemUTC(), counterOrderService);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        encargado = new User();
        encargado.setId(UUID.randomUUID());
        encargado.setRole(UserRole.ENCARGADO);
        encargado.setTenantId(UUID.randomUUID());
        orderId = UUID.randomUUID();
    }

    @Test
    @DisplayName("declares POST /orders/{orderId}/complete-delivery restricted to ENCARGADO and ADMINISTRADOR")
    void declaresRouteAndRoles() throws NoSuchMethodException {
        Method method = CounterOrderController.class.getMethod("completeDelivery", UUID.class, User.class);

        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly("/orders/{orderId}/complete-delivery");
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("ENCARGADO").contains("ADMINISTRADOR").doesNotContain("COMENSAL");
    }

    @Test
    @DisplayName("200 with the contract 01 payload, passing the authenticated user to the service")
    void returnsCompletionPayload() throws Exception {
        when(counterOrderService.completeDelivery(eq(orderId), eq(encargado))).thenReturn(new CompleteDeliveryResponse(
                orderId, 42, OrderStatus.COMPLETED, COMPLETED_AT, "Entrega completada exitosamente."));

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.ticketNumber").value(42))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").value("2026-11-15T18:30:00Z"))
                .andExpect(jsonPath("$.message").value("Entrega completada exitosamente."));

        verify(counterOrderService).completeDelivery(orderId, encargado);
    }

    @Test
    @DisplayName("200 for the idempotent retry, with its own message")
    void returnsIdempotentPayload() throws Exception {
        when(counterOrderService.completeDelivery(eq(orderId), eq(encargado))).thenReturn(new CompleteDeliveryResponse(
                orderId, 42, OrderStatus.COMPLETED, COMPLETED_AT,
                "La orden ya se encontraba completada. Respuesta idempotente."));

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("La orden ya se encontraba completada. Respuesta idempotente."));
    }

    @Test
    @DisplayName("409 with code INVALID_ORDER_STATE when the order is not ready")
    void mapsInvalidStateTo409() throws Exception {
        when(counterOrderService.completeDelivery(eq(orderId), eq(encargado))).thenThrow(new InvalidOrderStateException(
                "No se puede entregar una orden que se encuentra en estado IN_PROGRESS. Debe estar en estado READY_FOR_PICKUP."));

        perform()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("IN_PROGRESS")));
    }

    @Test
    @DisplayName("404 with code ORDER_NOT_FOUND for an unknown order")
    void mapsNotFoundTo404() throws Exception {
        when(counterOrderService.completeDelivery(eq(orderId), eq(encargado))).thenThrow(new OrderNotFoundException(orderId));

        perform()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("403 when the order belongs to another establishment")
    void mapsTenantViolationTo403() throws Exception {
        when(counterOrderService.completeDelivery(eq(orderId), eq(encargado)))
                .thenThrow(new TenantViolationException("La orden no pertenece al establecimiento del usuario autenticado."));

        perform().andExpect(status().isForbidden());
    }

    private ResultActions perform() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                encargado, null, List.of(new SimpleGrantedAuthority("ROLE_ENCARGADO")));
        return mockMvc.perform(post("/api/v1/counter/orders/{orderId}/complete-delivery", orderId).principal(auth));
    }
}
