package com.easyeat.order.integration;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.common.SqlQueryCountAssertionHelper;
import com.easyeat.operations.cache.OperatingScheduleCache;
import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.repository.OrderRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("PickupSlotGroupedQueryIntegrationTest (T036, FR-003, SC-002, SC-005)")
class PickupSlotGroupedQueryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_pickup_slot_grouped_it")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private OperatingScheduleRepository operatingScheduleRepository;

    @Autowired
    private OperatingScheduleCache operatingScheduleCache;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    private SqlQueryCountAssertionHelper sqlHelper;
    private UUID establishmentId;
    private User comensalUser;
    private LocalDate targetDate;

    @BeforeEach
    void setUp() {
        sqlHelper = SqlQueryCountAssertionHelper.from(entityManagerFactory);
        orderRepository.deleteAll();
        operatingScheduleRepository.deleteAll();
        operatingScheduleCache.evictAll();
        userRepository.deleteAll();
        establishmentRepository.deleteAll();

        ZoneId zoneId = ZoneId.of("America/Argentina/Buenos_Aires");
        targetDate = LocalDate.now(zoneId).plusDays(2);

        Establishment est = new Establishment(
                null,
                "Restaurante Turnos Agrupados",
                EstablishmentStatus.ACTIVE,
                LocalTime.of(9, 0),
                LocalTime.of(21, 0), // 12h = 48 slots of 15 min
                "America/Argentina/Buenos_Aires"
        );
        est.setMaxCapacityPerSlot(10);
        est.setLeadTimeMinutes(30);
        est.setOperatingDays("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY");
        est = establishmentRepository.save(est);
        establishmentId = est.getId();

        for (DayOfWeek day : DayOfWeek.values()) {
            operatingScheduleRepository.save(new EstablishmentOperatingSchedule(
                    establishmentId,
                    day,
                    LocalTime.of(9, 0),
                    LocalTime.of(21, 0),
                    false
            ));
        }
        // Pre-warm schedule cache so HTTP request measures establishment + 1 grouped orders query
        operatingScheduleCache.getWeekMap(establishmentId);

        comensalUser = new User();
        comensalUser.setEmail("comensal.slots@easyeat.com");
        comensalUser.setPasswordHash("$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyha");
        comensalUser.setFirstName("Valen");
        comensalUser.setLastName("Carmona");
        comensalUser.setRole(UserRole.COMENSAL);
        comensalUser.setStatus(UserStatus.ACTIVE);
        comensalUser.setMustChangePassword(false);
        comensalUser = userRepository.save(comensalUser);

        Instant futureExpiry = Instant.now().plus(10, ChronoUnit.MINUTES);
        Instant pastExpiry = Instant.now().minus(10, ChronoUnit.MINUTES);

        // Slot 12:00: 8 computable orders (HIGH_DEMAND) + 1 expired PENDING_PAYMENT + 1 CANCELLED (ignored)
        saveOrders(LocalTime.of(12, 0), OrderStatus.PAID, 2, null);
        saveOrders(LocalTime.of(12, 0), OrderStatus.CONFIRMED, 2, null);
        saveOrders(LocalTime.of(12, 0), OrderStatus.IN_PROGRESS, 1, null);
        saveOrders(LocalTime.of(12, 0), OrderStatus.READY_FOR_PICKUP, 1, null);
        saveOrders(LocalTime.of(12, 0), OrderStatus.PENDING_PAYMENT, 2, futureExpiry);
        saveOrders(LocalTime.of(12, 0), OrderStatus.PENDING_PAYMENT, 1, pastExpiry);
        saveOrders(LocalTime.of(12, 0), OrderStatus.CANCELLED, 1, null);

        // Slot 12:15: 10 computable orders (SOLD_OUT -> hidden)
        saveOrders(LocalTime.of(12, 15), OrderStatus.PAID, 10, null);

        // Slot 12:30: 7 computable orders (AVAILABLE -> 70%)
        saveOrders(LocalTime.of(12, 30), OrderStatus.PAID, 7, null);
    }

    private void saveOrders(LocalTime slotStart, OrderStatus status, int count, Instant expiresAt) {
        LocalTime slotEnd = slotStart.plusMinutes(15);
        for (int i = 0; i < count; i++) {
            Order order = new Order(
                    null,
                    establishmentId,
                    comensalUser.getId(),
                    FulfillmentMode.SCHEDULED,
                    targetDate,
                    slotStart,
                    slotEnd,
                    status,
                    new BigDecimal("5000.00"),
                    expiresAt
            );
            orderRepository.save(order);
        }
    }

    private UsernamePasswordAuthenticationToken comensalAuth() {
        return new UsernamePasswordAuthenticationToken(
                comensalUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );
    }

    @Test
    @DisplayName("GET /pickup-slots executes 1 grouped query on orders for 48 slots and reflects new orders immediately")
    void shouldExecuteSingleGroupedQueryAcross48SlotsAndReflectNewOrderImmediately() throws Exception {
        // Warm up user query / Hibernate session baseline: establishment (1) + grouped orders (1) + user auth (0-1) <= 3 SQL
        sqlHelper.clear();

        mockMvc.perform(get("/api/v1/establishments/{id}/pickup-slots", establishmentId)
                        .param("date", targetDate.toString())
                        .with(authentication(comensalAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOperatingDay").value(true))
                // 48 total slots - 1 SOLD_OUT (12:15) = 47 selectable slots
                .andExpect(jsonPath("$.slots.length()").value(47))
                .andExpect(jsonPath("$.slots[?(@.startTime=='12:00:00')].bookedCount").value(hasItem(8)))
                .andExpect(jsonPath("$.slots[?(@.startTime=='12:00:00')].status").value(hasItem("HIGH_DEMAND")))
                .andExpect(jsonPath("$.slots[?(@.startTime=='12:30:00')].bookedCount").value(hasItem(7)))
                .andExpect(jsonPath("$.slots[?(@.startTime=='12:30:00')].status").value(hasItem("AVAILABLE")))
                .andExpect(jsonPath("$.slots[?(@.startTime=='12:15:00')]").value(not(hasItem(10))));

        long sqlCount = sqlHelper.getPrepareStatementCount();
        // With N+1 this would execute 48+ queries; grouped query keeps total <= 3 (establishment + 1 grouped orders)
        assertThat(sqlCount).isLessThanOrEqualTo(3L);

        // SC-005: Creating a new computable order in 12:30 immediately transitions it to 8/10 HIGH_DEMAND
        saveOrders(LocalTime.of(12, 30), OrderStatus.PAID, 1, null);

        sqlHelper.clear();
        mockMvc.perform(get("/api/v1/establishments/{id}/pickup-slots", establishmentId)
                        .param("date", targetDate.toString())
                        .with(authentication(comensalAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.startTime=='12:30:00')].bookedCount").value(hasItem(8)))
                .andExpect(jsonPath("$.slots[?(@.startTime=='12:30:00')].status").value(hasItem("HIGH_DEMAND")));

        assertThat(sqlHelper.getPrepareStatementCount()).isLessThanOrEqualTo(3L);
    }
}
