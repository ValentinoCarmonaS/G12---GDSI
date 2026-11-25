package com.easyeat.order;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.controller.CounterOrderController;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.profile.repository.UserPhotoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CounterOrderControllerIntegrationTest (T019, US3, ENC-2.1.1, RF-005, RF-017)")
class CounterOrderControllerIntegrationTest {

    private static final ZoneId BUENOS_AIRES_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private MockMvc mockMvc;

    @Mock
    private KdsTicketRepository kdsTicketRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPhotoRepository userPhotoRepository;

    private Clock fixedClock;
    private CounterOrderController controller;
    private UUID establishmentId;
    private User encargadoUser;
    private UsernamePasswordAuthenticationToken encargadoAuth;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-15T16:35:00Z"), BUENOS_AIRES_ZONE);
        controller = new CounterOrderController(
                kdsTicketRepository,
                orderRepository,
                userRepository,
                userPhotoRepository,
                fixedClock
        );

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        establishmentId = UUID.randomUUID();
        encargadoUser = new User();
        encargadoUser.setId(UUID.randomUUID());
        encargadoUser.setEmail("encargado@easyeat.com");
        encargadoUser.setRole(UserRole.ENCARGADO);
        encargadoUser.setTenantId(establishmentId);

        encargadoAuth = new UsernamePasswordAuthenticationToken(
                encargadoUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ENCARGADO"))
        );
    }

    @Test
    @DisplayName("T019/T022: GET /api/v1/counter/orders declares @PreAuthorize restricting to ENCARGADO and ADMINISTRADOR")
    void shouldDeclarePreAuthorizeForEncargadoAndAdministrador() throws Exception {
        Method method = CounterOrderController.class.getMethod(
                "getCounterReadyOrders",
                String.class,
                UUID.class,
                User.class
        );
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value())
                .contains("ENCARGADO")
                .contains("ADMINISTRADOR");
    }

    @Test
    @DisplayName("T019: GET /api/v1/counter/orders with role ENCARGADO filters by pickupDate = today and returns deterministic order readyAt ASC, ticketNumber ASC")
    void shouldReturnTodayCounterReadyOrdersOrderedDeterministically() throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 15);
        Establishment establishment = new Establishment();
        establishment.setId(establishmentId);

        UUID ticketId104 = UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d");
        UUID orderId104 = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        UUID customerId104 = UUID.fromString("c71e2289-4b68-45e0-a7d1-e6e7d6928e01");

        UUID ticketId105 = UUID.fromString("a15c329b-81d3-4e89-b620-7f2cb14197e2");
        UUID orderId105 = UUID.fromString("50e932b1-12c8-4ec7-9104-58a47de0281b");
        UUID customerId105 = UUID.fromString("d98f1214-9271-4899-8ab3-772c21980a34");

        KdsTicket ticket104 = new KdsTicket(
                ticketId104,
                orderId104,
                establishment,
                104,
                KdsTicketStatus.READY_FOR_PICKUP,
                FulfillmentMode.IMMEDIATE,
                today,
                LocalTime.of(13, 0),
                LocalTime.of(13, 15),
                Instant.parse("2026-11-15T16:15:00Z")
        );
        ticket104.setReadyAt(Instant.parse("2026-11-15T16:30:00Z"));

        KdsTicket ticket105 = new KdsTicket(
                ticketId105,
                orderId105,
                establishment,
                105,
                KdsTicketStatus.READY_FOR_PICKUP,
                FulfillmentMode.SCHEDULED,
                today,
                LocalTime.of(13, 15),
                LocalTime.of(13, 30),
                Instant.parse("2026-11-15T16:18:00Z")
        );
        ticket105.setReadyAt(Instant.parse("2026-11-15T16:33:45Z"));

        Order order104 = new Order();
        order104.setId(orderId104);
        order104.setEstablishmentId(establishmentId);
        order104.setUserId(customerId104);
        order104.setPickupDate(today);
        order104.setTimeSlotStart(LocalTime.of(13, 0));
        order104.setTimeSlotEnd(LocalTime.of(13, 15));
        order104.setStatus(OrderStatus.READY_FOR_PICKUP);
        order104.setReadyAt(Instant.parse("2026-11-15T16:30:00Z"));
        order104.assignSecurityCodeIfAbsent("4821");
        order104.setTotalAmount(new BigDecimal("18500.00"));
        order104.addItem(new OrderItem(order104, UUID.randomUUID(), "Hamburguesa Completa con Papas", new BigDecimal("8000.00"), 2, new BigDecimal("16000.00")));
        order104.addItem(new OrderItem(order104, UUID.randomUUID(), "Limonada con Menta y Jengibre", new BigDecimal("2500.00"), 1, new BigDecimal("2500.00")));

        Order order105 = new Order();
        order105.setId(orderId105);
        order105.setEstablishmentId(establishmentId);
        order105.setUserId(customerId105);
        order105.setPickupDate(today);
        order105.setTimeSlotStart(LocalTime.of(13, 15));
        order105.setTimeSlotEnd(LocalTime.of(13, 30));
        order105.setStatus(OrderStatus.READY_FOR_PICKUP);
        order105.setReadyAt(Instant.parse("2026-11-15T16:33:45Z"));
        order105.assignSecurityCodeIfAbsent("9102");
        order105.setTotalAmount(new BigDecimal("9500.00"));
        order105.addItem(new OrderItem(order105, UUID.randomUUID(), "Ensalada Caesar con Pollo", new BigDecimal("9500.00"), 1, new BigDecimal("9500.00")));

        User diner104 = new User();
        diner104.setId(customerId104);
        diner104.setFullName("Valentino Carmona");

        User diner105 = new User();
        diner105.setId(customerId105);
        diner105.setFullName("Lucía Morales");

        when(kdsTicketRepository.findCounterReadyTickets(establishmentId, today))
                .thenReturn(List.of(ticket104, ticket105));
        when(orderRepository.findAllByIdWithItems(List.of(orderId104, orderId105)))
                .thenReturn(List.of(order104, order105));
        when(userRepository.findAllById(List.of(customerId104, customerId105)))
                .thenReturn(List.of(diner104, diner105));
        when(userPhotoRepository.findUserIdsWithPhoto(List.of(customerId104, customerId105)))
                .thenReturn(List.of(customerId104));

        mockMvc.perform(get("/api/v1/counter/orders")
                        .principal(encargadoAuth)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ticketId").value(ticketId104.toString()))
                .andExpect(jsonPath("$[0].ticketNumber").value(104))
                .andExpect(jsonPath("$[0].timeSlot.start").value("13:00"))
                .andExpect(jsonPath("$[0].timeSlot.end").value("13:15"))
                .andExpect(jsonPath("$[1].ticketId").value(ticketId105.toString()))
                .andExpect(jsonPath("$[1].ticketNumber").value(105))
                .andExpect(jsonPath("$[1].timeSlot.start").value("13:15"))
                .andExpect(jsonPath("$[1].timeSlot.end").value("13:30"));

        verify(kdsTicketRepository).findCounterReadyTickets(establishmentId, today);
        verify(orderRepository, never()).findById(any());
        verify(userRepository, never()).findById(any());
        verify(userPhotoRepository, never()).existsByUserId(any());
    }

    @Test
    @DisplayName("T026: GET /api/v1/counter/orders returns 4-digit securityCode, customer.fullName, customer.photoUrl, typed items and zero financial fields")
    void shouldReturnSecurityCodeCustomerItemsAndExcludeAllFinancialFields() throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 15);
        Establishment establishment = new Establishment();
        establishment.setId(establishmentId);

        UUID ticketId104 = UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d");
        UUID orderId104 = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        UUID customerId104 = UUID.fromString("c71e2289-4b68-45e0-a7d1-e6e7d6928e01");

        UUID ticketId105 = UUID.fromString("a15c329b-81d3-4e89-b620-7f2cb14197e2");
        UUID orderId105 = UUID.fromString("50e932b1-12c8-4ec7-9104-58a47de0281b");
        UUID customerId105 = UUID.fromString("d98f1214-9271-4899-8ab3-772c21980a34");

        KdsTicket ticket104 = new KdsTicket(
                ticketId104,
                orderId104,
                establishment,
                104,
                KdsTicketStatus.READY_FOR_PICKUP,
                FulfillmentMode.IMMEDIATE,
                today,
                LocalTime.of(13, 0),
                LocalTime.of(13, 15),
                Instant.parse("2026-11-15T16:15:00Z")
        );
        ticket104.setReadyAt(Instant.parse("2026-11-15T16:30:00Z"));

        KdsTicket ticket105 = new KdsTicket(
                ticketId105,
                orderId105,
                establishment,
                105,
                KdsTicketStatus.READY_FOR_PICKUP,
                FulfillmentMode.SCHEDULED,
                today,
                LocalTime.of(13, 15),
                LocalTime.of(13, 30),
                Instant.parse("2026-11-15T16:18:00Z")
        );
        ticket105.setReadyAt(Instant.parse("2026-11-15T16:33:45Z"));

        Order order104 = new Order();
        order104.setId(orderId104);
        order104.setEstablishmentId(establishmentId);
        order104.setUserId(customerId104);
        order104.setPickupDate(today);
        order104.setTimeSlotStart(LocalTime.of(13, 0));
        order104.setTimeSlotEnd(LocalTime.of(13, 15));
        order104.setStatus(OrderStatus.READY_FOR_PICKUP);
        order104.setReadyAt(Instant.parse("2026-11-15T16:30:00Z"));
        order104.assignSecurityCodeIfAbsent("4821");
        order104.setTotalAmount(new BigDecimal("18500.00"));
        order104.addItem(new OrderItem(order104, UUID.randomUUID(), "Hamburguesa Completa con Papas", new BigDecimal("8000.00"), 2, new BigDecimal("16000.00")));
        order104.addItem(new OrderItem(order104, UUID.randomUUID(), "Limonada con Menta y Jengibre", new BigDecimal("2500.00"), 1, new BigDecimal("2500.00")));

        // Legacy/unfilled securityCode on order105: the counter shows no code, never a derived one (Spec 022 T016b, edge case 10)
        Order order105 = new Order();
        order105.setId(orderId105);
        order105.setEstablishmentId(establishmentId);
        order105.setUserId(customerId105);
        order105.setPickupDate(today);
        order105.setTimeSlotStart(LocalTime.of(13, 15));
        order105.setTimeSlotEnd(LocalTime.of(13, 30));
        order105.setStatus(OrderStatus.READY_FOR_PICKUP);
        order105.setReadyAt(Instant.parse("2026-11-15T16:33:45Z"));
        order105.setTotalAmount(new BigDecimal("9500.00"));
        order105.addItem(new OrderItem(order105, UUID.randomUUID(), "Ensalada Caesar con Pollo", new BigDecimal("9500.00"), 1, new BigDecimal("9500.00")));

        User diner104 = new User();
        diner104.setId(customerId104);
        diner104.setFullName("Valentino Carmona");

        User diner105 = new User();
        diner105.setId(customerId105);
        diner105.setFullName("Lucía Morales");

        when(kdsTicketRepository.findCounterReadyTickets(establishmentId, today))
                .thenReturn(List.of(ticket104, ticket105));
        when(orderRepository.findAllByIdWithItems(List.of(orderId104, orderId105)))
                .thenReturn(List.of(order104, order105));
        when(userRepository.findAllById(List.of(customerId104, customerId105)))
                .thenReturn(List.of(diner104, diner105));
        when(userPhotoRepository.findUserIdsWithPhoto(List.of(customerId104, customerId105)))
                .thenReturn(List.of(customerId104));

        String responseBody = mockMvc.perform(get("/api/v1/counter/orders")
                        .principal(encargadoAuth)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].securityCode").value("4821"))
                .andExpect(jsonPath("$[0].customer.userId").value(customerId104.toString()))
                .andExpect(jsonPath("$[0].customer.fullName").value("Valentino Carmona"))
                .andExpect(jsonPath("$[0].customer.photoUrl").value("/api/v1/profile/users/" + customerId104 + "/avatar"))
                .andExpect(jsonPath("$[0].items.length()").value(2))
                .andExpect(jsonPath("$[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$[0].items[0].dishName").value("Hamburguesa Completa con Papas"))
                .andExpect(jsonPath("$[0].items[1].quantity").value(1))
                .andExpect(jsonPath("$[0].items[1].dishName").value("Limonada con Menta y Jengibre"))
                .andExpect(jsonPath("$[1].customer.userId").value(customerId105.toString()))
                .andExpect(jsonPath("$[1].customer.fullName").value("Lucía Morales"))
                .andExpect(jsonPath("$[1].customer.photoUrl").doesNotExist())
                .andExpect(jsonPath("$[1].items.length()").value(1))
                .andExpect(jsonPath("$[1].items[0].quantity").value(1))
                .andExpect(jsonPath("$[1].items[0].dishName").value("Ensalada Caesar con Pollo"))
                .andExpect(jsonPath("$[0].totalAmount").doesNotExist())
                .andExpect(jsonPath("$[0].items[0].unitPrice").doesNotExist())
                .andExpect(jsonPath("$[0].items[0].subtotal").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody)
                .doesNotContain("unitPrice")
                .doesNotContain("subtotal")
                .doesNotContain("totalAmount")
                .doesNotContain("18500")
                .doesNotContain("8000");

        com.fasterxml.jackson.databind.JsonNode root = new ObjectMapper().readTree(responseBody);
        assertThat(root.get(1).get("securityCode").isNull()).isTrue();

        verify(orderRepository, never()).findById(any());
        verify(userRepository, never()).findById(any());
        verify(userPhotoRepository, never()).existsByUserId(any());
    }

    @Test
    @DisplayName("T019: GET /api/v1/counter/orders excludes tickets from future dates (pickupDate > today)")
    void shouldExcludeFutureDatedTicketsFromCounterReadyQueue() throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 15);
        LocalDate tomorrow = today.plusDays(1);
        Establishment establishment = new Establishment();
        establishment.setId(establishmentId);

        KdsTicket futureTicket = new KdsTicket(
                UUID.randomUUID(),
                UUID.randomUUID(),
                establishment,
                201,
                KdsTicketStatus.READY_FOR_PICKUP,
                FulfillmentMode.SCHEDULED,
                tomorrow,
                LocalTime.of(13, 0),
                LocalTime.of(13, 15),
                Instant.parse("2026-11-15T16:00:00Z")
        );
        futureTicket.setReadyAt(Instant.parse("2026-11-15T16:10:00Z"));

        when(kdsTicketRepository.findCounterReadyTickets(establishmentId, today))
                .thenReturn(List.of(futureTicket));

        mockMvc.perform(get("/api/v1/counter/orders")
                        .principal(encargadoAuth)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
