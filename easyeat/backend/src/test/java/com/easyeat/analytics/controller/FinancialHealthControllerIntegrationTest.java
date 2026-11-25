package com.easyeat.analytics.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.Order;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FinancialHealthControllerIntegrationTest {

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
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID tenantId;
    private UUID adminId;
    private String adminToken;
    private String chefToken;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM dishes");
        userRepository.deleteAll();
        establishmentRepository.deleteAll();

        Establishment est = new Establishment();
        est.setName("Test Bistro");
        est.setTimezone("America/Argentina/Buenos_Aires");
        est = establishmentRepository.save(est);
        tenantId = est.getId();

        User admin = new User();
        admin.setEmail("admin@bistro.com");
        admin.setPasswordHash(passwordEncoder.encode("Secret123!"));
        admin.setRole(UserRole.ADMINISTRADOR);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setTenantId(tenantId);
        admin = userRepository.save(admin);
        adminId = admin.getId();
        adminToken = jwtService.generateToken(admin);

        User chef = new User();
        chef.setEmail("chef@bistro.com");
        chef.setPasswordHash(passwordEncoder.encode("Secret123!"));
        chef.setRole(UserRole.CHEF);
        chef.setStatus(UserStatus.ACTIVE);
        chef.setTenantId(tenantId);
        chef = userRepository.save(chef);
        chefToken = jwtService.generateToken(chef);
    }

    private void createOrder(OrderStatus status, BigDecimal amount, Instant paidAt) {
        Order order = new Order();
        order.setEstablishmentId(tenantId);
        order.setUserId(adminId);
        order.setFulfillmentMode(com.easyeat.order.domain.FulfillmentMode.values()[0]);
        order.setPickupDate(java.time.LocalDate.now());
        order.setTimeSlotStart(java.time.LocalTime.of(12, 0));
        order.setTimeSlotEnd(java.time.LocalTime.of(12, 30));
        order.setStatus(status);
        order.setTotalAmount(amount);
        order.setPaidAt(paidAt);
        orderRepository.save(order);
    }

    @Test
    void getFinancialHealth_dayWeekMonth_returns200AndExcludesNonCreditedOrders() throws Exception {
        ZoneId zone = ZoneId.of("America/Argentina/Buenos_Aires");
        Instant todayNoon = LocalDate.now(zone).atTime(12, 0).atZone(zone).toInstant();
        Instant todayEnd = LocalDate.now(zone).atTime(23, 59, 59).atZone(zone).toInstant();

        createOrder(OrderStatus.PAID, new BigDecimal("100.50"), todayNoon);
        createOrder(OrderStatus.COMPLETED, new BigDecimal("200.00"), todayEnd);
        createOrder(OrderStatus.CANCELLED, new BigDecimal("500.00"), todayNoon);
        createOrder(OrderStatus.EXPIRED, new BigDecimal("300.00"), todayNoon);
        createOrder(OrderStatus.PENDING_PAYMENT, new BigDecimal("400.00"), null);

        mockMvc.perform(get("/api/v1/admin/analytics/financial-health")
                        .param("granularity", "day")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")))
                .andExpect(jsonPath("$.granularity").value("day"))
                .andExpect(jsonPath("$.revenue").value(300.50))
                .andExpect(jsonPath("$.orderCount").value(2));

        mockMvc.perform(get("/api/v1/admin/analytics/financial-health")
                        .param("granularity", "week")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("week"));

        mockMvc.perform(get("/api/v1/admin/analytics/financial-health")
                        .param("granularity", "month")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("month"));
    }

    @Test
    void getFinancialHealth_invalidGranularity_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/financial-health")
                        .param("granularity", "year")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid granularity 'year'. Supported values are: day, week, month"));
    }

    @Test
    void getFinancialHealth_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/financial-health")
                        .header("Authorization", "Bearer " + chefToken))
                .andExpect(status().isForbidden());
    }
}
