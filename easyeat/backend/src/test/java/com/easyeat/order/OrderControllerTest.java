package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.order.controller.OrderController;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.CreateOrderItemRequest;
import com.easyeat.order.dto.CreateOrderRequest;
import com.easyeat.order.dto.OrderCreatedResponse;
import com.easyeat.order.dto.OrderDetailResponse;
import com.easyeat.order.dto.OrderSummaryResponse;
import com.easyeat.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController Unit Tests")
class OrderControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private User testUser;
    private UsernamePasswordAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("comensal@test.com");

        auth = new UsernamePasswordAuthenticationToken(
                testUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );
    }

    @Test
    @DisplayName("POST /api/v1/orders returns 201 Created and OrderCreatedResponse")
    void createOrderReturns201() throws Exception {
        UUID establishmentId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(
                establishmentId,
                FulfillmentMode.SCHEDULED,
                LocalDate.now().plusDays(1),
                LocalTime.of(13, 0),
                LocalTime.of(13, 15),
                List.of(new CreateOrderItemRequest(UUID.randomUUID(), 1))
        );

        OrderCreatedResponse response = new OrderCreatedResponse(
                UUID.randomUUID(),
                establishmentId,
                FulfillmentMode.SCHEDULED,
                request.getPickupDate(),
                request.getTimeSlotStart(),
                request.getTimeSlotEnd(),
                OrderStatus.PENDING_PAYMENT,
                new BigDecimal("5000.00"),
                Instant.now().plusSeconds(900),
                900L,
                Instant.now()
        );

        when(orderService.createOrder(eq(testUser.getId()), any(CreateOrderRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.fulfillmentMode").value("SCHEDULED"));

        verify(orderService).createOrder(eq(testUser.getId()), any(CreateOrderRequest.class));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} returns 200 OK and OrderDetailResponse")
    void getOrderByIdReturns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderDetailResponse detail = new OrderDetailResponse(
                orderId,
                UUID.randomUUID(),
                FulfillmentMode.SCHEDULED,
                LocalDate.now().plusDays(1),
                LocalTime.of(13, 0),
                LocalTime.of(13, 15),
                OrderStatus.PENDING_PAYMENT,
                new BigDecimal("5000.00"),
                Instant.now().plusSeconds(900),
                900L,
                Instant.now(),
                List.of()
        );

        when(orderService.getOrderById(orderId, testUser.getId())).thenReturn(detail);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        verify(orderService).getOrderById(orderId, testUser.getId());
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/cancel returns 200 OK and OrderSummaryResponse")
    void cancelOrderReturns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderSummaryResponse summary = new OrderSummaryResponse(
                orderId,
                OrderStatus.CANCELLED,
                Instant.now()
        );

        when(orderService.cancelOrder(orderId, testUser.getId())).thenReturn(summary);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(orderService).cancelOrder(orderId, testUser.getId());
    }
}
