package com.easyeat.analytics.integration;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.PaymentConfirmationRequest;
import com.easyeat.order.dto.PaymentConfirmationResponse;
import com.easyeat.order.dto.ZeroAmountOrderRequest;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.service.CheckoutConfirmationService;
import com.easyeat.order.service.CounterOrderService;
import com.easyeat.order.service.KdsQueueService;
import com.easyeat.order.service.OrderConfirmationTrackingService;
import com.easyeat.order.service.OrderLiveTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 028 US5 (T074): verifies Flyway backfill migration for orders.paid_at
 * (located by description per rule 5 of .specify/memory/migrations.md) and
 * end-to-end persistence of paid_at from payment confirmation through KDS
 * preparation, counter delivery, and immediate visibility in Admin Analytics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Spec 028 US5 - Order lifecycle to Admin Analytics integration (T074)")
class OrderLifecycleToAdminAnalyticsIntegrationTest {

    private static final String DESCRIPTION_SUFFIX = "__backfill_missing_orders_paid_at.sql";
    private static final String MIGRATIONS = "classpath*:db/migration/V*.sql";
    private static final ZoneId BUENOS_AIRES_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KdsTicketRepository kdsTicketRepository;

    @Autowired
    private CheckoutConfirmationService checkoutConfirmationService;

    @Autowired
    private OrderConfirmationTrackingService orderConfirmationTrackingService;

    @Autowired
    private KdsQueueService kdsQueueService;

    @Autowired
    private OrderLiveTrackingService orderLiveTrackingService;

    @Autowired
    private CounterOrderService counterOrderService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID establishmentId;
    private User adminUser;
    private User chefUser;
    private User counterUser;
    private User dinerUser;
    private String adminToken;
    private Dish milanesaDish;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Establishment est = new Establishment();
        est.setName("Bodegon US5 " + suffix);
        est.setTimezone("America/Argentina/Buenos_Aires");
        est = establishmentRepository.save(est);
        establishmentId = est.getId();

        adminUser = saveUser("admin-us5-" + suffix + "@easyeat.com", UserRole.ADMINISTRADOR, establishmentId);
        chefUser = saveUser("chef-us5-" + suffix + "@easyeat.com", UserRole.CHEF, establishmentId);
        counterUser = saveUser("counter-us5-" + suffix + "@easyeat.com", UserRole.ENCARGADO, establishmentId);
        dinerUser = saveUser("diner-us5-" + suffix + "@easyeat.com", UserRole.COMENSAL, null);
        adminToken = jwtService.generateToken(adminUser);

        milanesaDish = dishRepository.save(
                new Dish(establishmentId, "Milanesa Napolitana US5 " + suffix, "Clásica", new BigDecimal("2250.00"), true)
        );
    }

    @Test
    @DisplayName("(a) Flyway backfill migration exists once with unique version and repairs accredited orders with NULL paid_at")
    void backfillMigrationExistsOnceAndRepairsNullPaidAtOrders() throws IOException {
        List<Resource> all = Arrays.asList(new PathMatchingResourcePatternResolver().getResources(MIGRATIONS));
        List<Resource> matching = all.stream()
                .filter(r -> Objects.requireNonNull(r.getFilename()).endsWith(DESCRIPTION_SUFFIX))
                .toList();

        assertThat(matching)
                .as("migration *%s must exist exactly once on classpath", DESCRIPTION_SUFFIX)
                .hasSize(1);

        String version = versionOf(matching.get(0));
        long sameVersionCount = all.stream()
                .map(OrderLifecycleToAdminAnalyticsIntegrationTest::versionOf)
                .filter(version::equals)
                .count();
        assertThat(sameVersionCount)
                .as("Flyway version %s must belong to a single migration file", version)
                .isEqualTo(1);

        // Seed an accredited order with paid_at = NULL to verify the SQL backfill statement
        Order legacyOrder = createPendingOrder(new BigDecimal("3000.00"), 1);
        legacyOrder.setStatus(OrderStatus.COMPLETED);
        legacyOrder.setPaidAt(null);
        legacyOrder = orderRepository.saveAndFlush(legacyOrder);

        String sql = matching.get(0).getContentAsString(StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);

        Timestamp backfilledPaidAt = jdbcTemplate.queryForObject(
                "SELECT paid_at FROM orders WHERE id = ?",
                Timestamp.class,
                legacyOrder.getId()
        );
        assertThat(backfilledPaidAt)
                .as("Backfill migration must populate paid_at for accredited orders where paid_at IS NULL")
                .isNotNull();
    }

    @Test
    @DisplayName("(b) End-to-end confirmPayment -> KDS -> completeDelivery persists paid_at and reflects immediately in Admin Analytics")
    void endToEndOrderLifecyclePersistsPaidAtAndReflectsInAdminAnalytics() throws Exception {
        Order order = createPendingOrder(new BigDecimal("4500.00"), 2);

        // 1. Confirm payment as diner
        PaymentConfirmationResponse confirmation = checkoutConfirmationService.confirmPayment(
                dinerUser.getId(),
                new PaymentConfirmationRequest(order.getId(), "mp-us5-001", "approved")
        );
        assertThat(confirmation.kdsTicket()).isNotNull();

        Order afterConfirm = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(afterConfirm.getPaidAt())
                .as("confirmPayment must persist orders.paid_at")
                .isNotNull();

        // 2. Chef starts preparation and marks ready for pickup
        UUID ticketId = confirmation.kdsTicket().ticketId();
        kdsQueueService.startTicketPreparation(ticketId, establishmentId);
        orderLiveTrackingService.markTicketReadyForPickup(ticketId, chefUser.getId());

        // 3. Counter completes delivery
        counterOrderService.completeDelivery(order.getId(), counterUser);

        // 4. Admin Analytics immediately reflects the sale across day, week, month and all
        for (String granularity : List.of("day", "week", "month")) {
            mockMvc.perform(get("/api/v1/admin/analytics/financial-health")
                            .param("granularity", granularity)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.revenue").value(4500.0))
                    .andExpect(jsonPath("$.orderCount").value(1));
        }

        for (String granularity : List.of("day", "week", "month", "all")) {
            mockMvc.perform(get("/api/v1/admin/analytics/dish-ranking")
                            .param("granularity", granularity)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].dishId").value(milanesaDish.getId().toString()))
                    .andExpect(jsonPath("$[0].unitsSold").value(2));
        }
    }

    @Test
    @DisplayName("(b) confirmZeroAmountOrder and confirmAndApprovePayment also persist non-null orders.paid_at")
    void zeroAmountAndTrackingConfirmationPersistPaidAt() {
        Order zeroOrder = createPendingOrder(BigDecimal.ZERO, 1);
        checkoutConfirmationService.confirmZeroAmountOrder(
                dinerUser.getId(),
                new ZeroAmountOrderRequest(zeroOrder.getId())
        );
        Order savedZero = orderRepository.findById(zeroOrder.getId()).orElseThrow();
        assertThat(savedZero.getPaidAt())
                .as("confirmZeroAmountOrder must persist orders.paid_at")
                .isNotNull();

        Order trackingOrder = createPendingOrder(new BigDecimal("2250.00"), 1);
        orderConfirmationTrackingService.confirmAndApprovePayment(trackingOrder.getId());
        Order savedTracking = orderRepository.findById(trackingOrder.getId()).orElseThrow();
        assertThat(savedTracking.getPaidAt())
                .as("confirmAndApprovePayment must persist orders.paid_at")
                .isNotNull();
    }

    private User saveUser(String email, UserRole role, UUID tenantId) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Secret123!"));
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setTenantId(tenantId);
        return userRepository.save(u);
    }

    private Order createPendingOrder(BigDecimal totalAmount, int quantity) {
        Order order = new Order();
        order.setEstablishmentId(establishmentId);
        order.setUserId(dinerUser.getId());
        order.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        order.setPickupDate(LocalDate.now(BUENOS_AIRES_ZONE));
        order.setTimeSlotStart(LocalTime.of(12, 0));
        order.setTimeSlotEnd(LocalTime.of(12, 15));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(totalAmount);
        order.setExpiresAt(Instant.now().plusSeconds(900));

        OrderItem item = new OrderItem();
        item.setDishId(milanesaDish.getId());
        item.setDishName(milanesaDish.getName());
        item.setQuantity(quantity);
        BigDecimal unitPrice = totalAmount.compareTo(BigDecimal.ZERO) > 0 && quantity > 0
                ? totalAmount.divide(BigDecimal.valueOf(quantity))
                : new BigDecimal("2250.00");
        item.setUnitPrice(unitPrice);
        item.setSubtotal(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        order.addItem(item);

        return orderRepository.saveAndFlush(order);
    }

    private static String versionOf(Resource resource) {
        String filename = Objects.requireNonNull(resource.getFilename());
        return filename.substring(0, filename.indexOf("__"));
    }
}
