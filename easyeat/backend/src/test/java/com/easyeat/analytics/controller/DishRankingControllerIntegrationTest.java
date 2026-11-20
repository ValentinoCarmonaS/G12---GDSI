package com.easyeat.analytics.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DishRankingControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID establishmentId;
    private String adminToken;
    private String chefToken;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        userRepository.deleteAll();
        establishmentRepository.deleteAll();

        Establishment est = new Establishment();
        est.setName("Restaurante Ranking");
        est.setSlug("restaurante-ranking-" + UUID.randomUUID().toString().substring(0, 8));
        est.setTimezone("America/Argentina/Buenos_Aires");
        est = establishmentRepository.save(est);
        establishmentId = est.getId();

        User admin = new User();
        admin.setEmail("admin-ranking@easyeat.com");
        admin.setPasswordHash(passwordEncoder.encode("Secret123!"));
        admin.setRole(UserRole.ADMINISTRADOR);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setEstablishmentId(establishmentId);
        admin = userRepository.save(admin);

        User chef = new User();
        chef.setEmail("chef-ranking@easyeat.com");
        chef.setPasswordHash(passwordEncoder.encode("Secret123!"));
        chef.setRole(UserRole.COCINERO);
        chef.setStatus(UserStatus.ACTIVE);
        chef.setEstablishmentId(establishmentId);
        chef = userRepository.save(chef);

        adminToken = jwtService.generateAccessToken(admin);
        chefToken = jwtService.generateAccessToken(chef);
    }

    private Order createOrder(OrderStatus status, Instant paidAt, List<OrderItem> items) {
        Order order = new Order();
        order.setEstablishmentId(establishmentId);
        order.setStatus(status);
        order.setPaidAt(paidAt);
        order.setTotalAmount(new BigDecimal("1000.00"));
        order.setCustomerName("Cliente Test");
        order.setCustomerEmail("cliente@test.com");
        for (OrderItem item : items) {
            item.setOrder(order);
        }
        order.setItems(items);
        return orderRepository.save(order);
    }

    private OrderItem item(UUID dishId, String dishName, int quantity) {
        OrderItem oi = new OrderItem();
        oi.setDishId(dishId);
        oi.setDishName(dishName);
        oi.setQuantity(quantity);
        oi.setUnitPrice(new BigDecimal("500.00"));
        return oi;
    }

    @Test
    void getDishRanking_emptySales_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/dish-ranking")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getDishRanking_allAndPeriods_sortsDescWithAzTieBreakerAndExcludesNonCompleted() throws Exception {
        UUID empanadaId = UUID.randomUUID();
        UUID pizzaId = UUID.randomUUID();
        UUID burgerId = UUID.randomUUID();

        Instant now = Instant.now();
        Instant lastMonth = now.minus(40, ChronoUnit.DAYS);

        createOrder(OrderStatus.PAID, now, List.of(
                item(empanadaId, "Empanada de Carne Suave", 5),
                item(pizzaId, "Pizza Muzzarella", 3)
        ));
        createOrder(OrderStatus.COMPLETED, lastMonth, List.of(
                item(burgerId, "Hamburguesa Clasica", 3)
        ));
        // Excluded statuses
        createOrder(OrderStatus.CANCELLED, now, List.of(
                item(pizzaId, "Pizza Muzzarella", 10)
        ));
        createOrder(OrderStatus.PENDING_PAYMENT, null, List.of(
                item(burgerId, "Hamburguesa Clasica", 10)
        ));

        // Default (all): Empanada (5), Hamburguesa (3), Pizza (3) -> H before P
        mockMvc.perform(get("/api/v1/admin/analytics/dish-ranking")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].dishName").value("Empanada de Carne Suave"))
                .andExpect(jsonPath("$[0].unitsSold").value(5))
                .andExpect(jsonPath("$[0].revenue").doesNotExist())
                .andExpect(jsonPath("$[1].dishName").value("Hamburguesa Clasica"))
                .andExpect(jsonPath("$[1].unitsSold").value(3))
                .andExpect(jsonPath("$[2].dishName").value("Pizza Muzzarella"))
                .andExpect(jsonPath("$[2].unitsSold").value(3));

        // Day: only today's order -> Empanada (5), Pizza (3)
        mockMvc.perform(get("/api/v1/admin/analytics/dish-ranking")
                        .param("granularity", "day")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].dishName").value("Empanada de Carne Suave"))
                .andExpect(jsonPath("$[1].dishName").value("Pizza Muzzarella"));
    }

    @Test
    void getDishRanking_invalidGranularity_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/dish-ranking")
                        .param("granularity", "quarter")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getDishRanking_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/dish-ranking")
                        .header("Authorization", "Bearer " + chefToken))
                .andExpect(status().isForbidden());
    }
}
