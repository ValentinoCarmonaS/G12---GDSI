package com.easyeat.order;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.order.controller.KitchenKdsController;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("T006: KitchenKdsController GET /api/v1/kitchen/orders Test")
class KitchenKdsControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private KdsTicketRepository kdsTicketRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private KitchenKdsController kitchenKdsController;

    private UUID establishmentId;
    private User chefUser;

    @BeforeEach
    void setUp() {
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(kitchenKdsController)
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        establishmentId = UUID.randomUUID();
        chefUser = new User();
        chefUser.setId(UUID.randomUUID());
        chefUser.setEmail("chef@easyeat.com");
        chefUser.setRole(UserRole.CHEF);
        chefUser.setTenantId(establishmentId);
    }

    @Test
    @DisplayName("GET /api/v1/kitchen/orders?activeQueue=true debe invocar findActiveQueueTickets y retornar startedAt")
    void shouldReturnActiveQueueOrdersWithStartedAtMapped() throws Exception {
        UUID orderId = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        Instant queuedAt = Instant.parse("2026-11-15T15:00:00Z");
        Instant startedAt = Instant.parse("2026-11-15T15:10:00Z");

        Establishment est = new Establishment();
        est.setId(establishmentId);
        est.setName("Test Resto");
        est.setStatus(EstablishmentStatus.ACTIVE);

        KdsTicket ticket = new KdsTicket(
                UUID.randomUUID(),
                orderId,
                est,
                101,
                KdsTicketStatus.IN_PREPARATION,
                FulfillmentMode.IMMEDIATE,
                today,
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                queuedAt
        );
        ticket.setStartedAt(startedAt);

        Order order = new Order();
        order.setId(orderId);
        order.setEstablishmentId(establishmentId);
        order.setUserId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("2500.00"));
        order.setStatus(OrderStatus.IN_PROGRESS);

        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setDishId(UUID.randomUUID());
        item.setDishName("Hamburguesa Especial");
        item.setQuantity(2);
        order.setItems(List.of(item));

        when(kdsTicketRepository.findActiveQueueTickets(eq(establishmentId), any(LocalDate.class)))
                .thenReturn(List.of(ticket));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                chefUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CHEF"))
        );

        mockMvc.perform(get("/api/v1/kitchen/orders")
                        .param("activeQueue", "true")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticketId").value(ticket.getId().toString()))
                .andExpect(jsonPath("$.content[0].ticketNumber").value(101))
                .andExpect(jsonPath("$.content[0].status").value("IN_PREPARATION"))
                .andExpect(jsonPath("$.content[0].startedAt").value(startedAt.toString()))
                .andExpect(jsonPath("$.content[0].items[0].dishName").value("Hamburguesa Especial"))
                .andExpect(jsonPath("$.content[0].items[0].quantity").value(2));
    }
}
