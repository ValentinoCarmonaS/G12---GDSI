package com.easyeat.order.integration;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.common.SqlQueryCountAssertionHelper;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishPhoto;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.InboxTab;
import com.easyeat.dish.repository.DishPhotoRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.dish.service.DishReviewInboxService;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.profile.domain.DietaryRestriction;
import com.easyeat.profile.domain.UserPhoto;
import com.easyeat.profile.repository.DietaryRestrictionRepository;
import com.easyeat.profile.repository.UserPhotoRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("StaffOperationsBatchQueryIntegrationTest (T043, FR-004, SC-002, F1, F8, E2)")
class StaffOperationsBatchQueryIntegrationTest {

    private static final ZoneId BUENOS_AIRES_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_staff_ops_batch_it")
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
    private UserRepository userRepository;

    @Autowired
    private UserPhotoRepository userPhotoRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KdsTicketRepository kdsTicketRepository;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private DishPhotoRepository dishPhotoRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DishReviewInboxService dishReviewInboxService;

    @Autowired
    private DietaryRestrictionRepository dietaryRestrictionRepository;

    private SqlQueryCountAssertionHelper sqlHelper;
    private Establishment establishment;
    private UUID establishmentId;
    private User encargadoUser;
    private User chefUser;
    private UsernamePasswordAuthenticationToken encargadoAuth;
    private UsernamePasswordAuthenticationToken chefAuth;

    @BeforeEach
    void setUp() {
        sqlHelper = SqlQueryCountAssertionHelper.from(entityManagerFactory);
        kdsTicketRepository.deleteAll();
        orderRepository.deleteAll();
        dishRecipeIngredientRepository.deleteAll();
        dishPhotoRepository.deleteAll();
        dishRepository.deleteAll();
        ingredientRepository.deleteAll();
        userPhotoRepository.deleteAll();
        userRepository.deleteAll();
        establishmentRepository.deleteAll();

        establishment = establishmentRepository.save(new Establishment(
                null,
                "Restaurante Operaciones Lote",
                EstablishmentStatus.ACTIVE,
                LocalTime.of(9, 0),
                LocalTime.of(22, 0),
                "America/Argentina/Buenos_Aires"
        ));
        establishmentId = establishment.getId();

        encargadoUser = new User();
        encargadoUser.setEmail("encargado-batch@easyeat.com");
        encargadoUser.setPasswordHash("hash");
        encargadoUser.setFullName("Encargado Batch");
        encargadoUser.setRole(UserRole.ENCARGADO);
        encargadoUser.setStatus(UserStatus.ACTIVE);
        encargadoUser.setTenantId(establishmentId);
        encargadoUser = userRepository.save(encargadoUser);

        chefUser = new User();
        chefUser.setEmail("chef-batch@easyeat.com");
        chefUser.setPasswordHash("hash");
        chefUser.setFullName("Chef Batch");
        chefUser.setRole(UserRole.CHEF);
        chefUser.setStatus(UserStatus.ACTIVE);
        chefUser.setTenantId(establishmentId);
        chefUser = userRepository.save(chefUser);

        encargadoAuth = new UsernamePasswordAuthenticationToken(
                encargadoUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ENCARGADO"))
        );
        chefAuth = new UsernamePasswordAuthenticationToken(
                chefUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CHEF"))
        );
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("GET /api/v1/counter/orders resuelve >= 10 comandas en <= 4 SQL constantes")
    void counterOrders_shouldExecuteAtMost4QueriesFor10PlusTickets() throws Exception {
        LocalDate today = LocalDate.now(BUENOS_AIRES_ZONE);
        Dish counterDish = new Dish();
        counterDish.setTenantId(establishmentId);
        counterDish.setName("Plato Mostrador");
        counterDish.setCategory(DishCategory.PLATOS_PRINCIPALES);
        counterDish.setDescription("Desc");
        counterDish.setStatus(DishStatus.PUBLISHED);
        counterDish.setIsActive(true);
        counterDish.setPausedByChef(false);
        counterDish.setAllergensConfirmed(true);
        counterDish.setCreatedBy(chefUser.getId());
        counterDish = dishRepository.save(counterDish);

        for (int i = 0; i < 12; i++) {
            User customer = new User();
            customer.setEmail("diner-counter-" + i + "@easyeat.com");
            customer.setPasswordHash("hash");
            customer.setFullName("Comensal " + i);
            customer.setRole(UserRole.COMENSAL);
            customer.setStatus(UserStatus.ACTIVE);
            customer = userRepository.save(customer);

            if (i % 2 == 0) {
                userPhotoRepository.save(new UserPhoto(
                        customer.getId(),
                        "avatar.png",
                        "image/png",
                        3L,
                        new byte[]{1, 2, 3}
                ));
            }

            Order order = new Order();
            order.setEstablishmentId(establishmentId);
            order.setUserId(customer.getId());
            order.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
            order.setPickupDate(today);
            order.setTimeSlotStart(LocalTime.of(12, 0));
            order.setTimeSlotEnd(LocalTime.of(12, 15));
            order.setStatus(OrderStatus.READY_FOR_PICKUP);
            order.setTotalAmount(new BigDecimal("5000.00"));
            order.assignSecurityCodeIfAbsent(String.format("%04d", 1000 + i));
            order.addItem(new OrderItem(order, counterDish.getId(), "Plato " + i, new BigDecimal("5000.00"), 1, new BigDecimal("5000.00")));
            order = orderRepository.save(order);

            KdsTicket ticket = new KdsTicket(
                    UUID.randomUUID(),
                    order.getId(),
                    establishment,
                    100 + i,
                    KdsTicketStatus.READY_FOR_PICKUP,
                    FulfillmentMode.IMMEDIATE,
                    today,
                    LocalTime.of(12, 0),
                    LocalTime.of(12, 15),
                    Instant.now()
            );
            ticket.setReadyAt(Instant.now().plusSeconds(i));
            kdsTicketRepository.save(ticket);
        }

        sqlHelper.clear();
        mockMvc.perform(get("/api/v1/counter/orders")
                        .with(authentication(encargadoAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12));

        assertThat(sqlHelper.getPrepareStatementCount()).isLessThanOrEqualTo(4L);
    }

    @Test
    @DisplayName("GET /api/v1/kitchen/dashboard/summary ejecuta exactamente 1 consulta SQL agregada")
    void kitchenDashboardSummary_shouldExecuteExactlyOneAggregatedQuery() throws Exception {
        LocalDate today = LocalDate.now();
        saveTicket(1, KdsTicketStatus.QUEUED, today);
        saveTicket(2, KdsTicketStatus.QUEUED, today);
        saveTicket(3, KdsTicketStatus.IN_PREPARATION, today);
        saveTicket(4, KdsTicketStatus.READY_FOR_PICKUP, today);
        saveTicket(5, KdsTicketStatus.COMPLETED, today);
        saveTicket(6, KdsTicketStatus.COMPLETED, today.minusDays(1));

        sqlHelper.clear();
        mockMvc.perform(get("/api/v1/kitchen/dashboard/summary")
                        .with(authentication(chefAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queuedOrdersCount").value(2))
                .andExpect(jsonPath("$.inPreparationOrdersCount").value(1))
                .andExpect(jsonPath("$.readyForPickupOrdersCount").value(1))
                .andExpect(jsonPath("$.completedTodayCount").value(1));

        assertThat(sqlHelper.getPrepareStatementCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /api/v1/dishes (<= 4 SQL), operational-status (<= 3 SQL, 0 N+1 restrictionCodes, 304) y Bandeja Admin")
    void chefDishesAndOperationalStatus_andAdminInbox_shouldMaintainConstantQueryCount() throws Exception {
        Ingredient activeIng = new Ingredient(
                establishmentId,
                "Harina",
                IngredientCategory.DRY_GOODS,
                UnitOfMeasure.KG,
                new BigDecimal("500.00")
        );
        activeIng.setStatus(IngredientStatus.ACTIVE);
        activeIng.setCurrentStock(new BigDecimal("100.00"));
        activeIng = ingredientRepository.save(activeIng);

        Set<String> restrictionCodes = dietaryRestrictionRepository.findAll().stream()
                .limit(2)
                .map(DietaryRestriction::getCode)
                .collect(java.util.stream.Collectors.toSet());

        for (int i = 0; i < 12; i++) {
            Dish dish = new Dish();
            dish.setTenantId(establishmentId);
            dish.setName("Plato Chef " + i);
            dish.setCategory(DishCategory.PLATOS_PRINCIPALES);
            dish.setDescription("Desc " + i);
            dish.setStatus(i < 6 ? DishStatus.PUBLISHED : DishStatus.PENDING_PRICE);
            dish.setIsActive(i < 6);
            dish.setPausedByChef(false);
            dish.setAllergensConfirmed(true);
            dish.setCreatedBy(chefUser.getId());
            dish.setRestrictionCodes(restrictionCodes);
            dish = dishRepository.save(dish);

            dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish.getId(), activeIng.getId(), new BigDecimal("1.00")));
            if (i % 2 == 0) {
                dishPhotoRepository.save(new DishPhoto(dish.getId(), "dish.jpg", "image/jpeg", 2L, new byte[]{1, 2}));
            }
        }

        sqlHelper.clear();
        mockMvc.perform(get("/api/v1/dishes")
                        .with(authentication(chefAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(12));
        assertThat(sqlHelper.getPrepareStatementCount()).isLessThanOrEqualTo(4L);

        sqlHelper.clear();
        MvcResult opResult = mockMvc.perform(get("/api/v1/dishes/operational-status")
                        .with(authentication(chefAuth)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.items.length()").value(12))
                .andReturn();
        assertThat(sqlHelper.getPrepareStatementCount()).isLessThanOrEqualTo(3L);

        String etag = opResult.getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();
        String unquotedEtag = etag.replace("\"", "");

        mockMvc.perform(get("/api/v1/dishes/operational-status")
                        .header("If-None-Match", etag)
                        .with(authentication(chefAuth)))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag));

        mockMvc.perform(get("/api/v1/dishes/operational-status")
                        .header("If-None-Match", unquotedEtag)
                        .with(authentication(chefAuth)))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag));

        // Bandeja de Revisión Admin (@BatchSize en restrictionCodes)
        TenantContextHolder.setTenantId(establishmentId);
        sqlHelper.clear();
        var inboxPage = dishReviewInboxService.getInbox(InboxTab.PENDING, 0, 20);
        assertThat(inboxPage.items()).hasSize(6);
        assertThat(sqlHelper.getPrepareStatementCount()).isLessThanOrEqualTo(6L);
    }

    private void saveTicket(int number, KdsTicketStatus status, LocalDate pickupDate) {
        Order order = new Order();
        order.setEstablishmentId(establishmentId);
        order.setUserId(encargadoUser.getId());
        order.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        order.setPickupDate(pickupDate);
        order.setTimeSlotStart(LocalTime.of(12, 0));
        order.setTimeSlotEnd(LocalTime.of(12, 15));
        order.setStatus(OrderStatus.PAID);
        order.setTotalAmount(new BigDecimal("1000.00"));
        order = orderRepository.save(order);

        KdsTicket ticket = new KdsTicket(
                UUID.randomUUID(),
                order.getId(),
                establishment,
                number,
                status,
                FulfillmentMode.IMMEDIATE,
                pickupDate,
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                Instant.now()
        );
        kdsTicketRepository.save(ticket);
    }
}
