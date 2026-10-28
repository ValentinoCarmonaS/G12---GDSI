package com.easyeat.dish.acceptance;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.AllergenUpdateRequest;
import com.easyeat.dish.dto.CreateDishRequest;
import com.easyeat.dish.dto.DishDetailResponse;
import com.easyeat.dish.dto.RecipeIngredientItem;
import com.easyeat.dish.dto.RecipeUpdateRequest;
import com.easyeat.dish.dto.UpdateDishRequest;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.IncompleteDishException;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.dish.service.DishService;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.notification.domain.AdminNotification;
import com.easyeat.notification.domain.NotificationType;
import com.easyeat.notification.dto.NotificationPageResponse;
import com.easyeat.notification.dto.NotificationResponse;
import com.easyeat.notification.repository.AdminNotificationRepository;
import com.easyeat.notification.service.AdminNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("User Story 4 Acceptance Test: Submission for Administrator Review (T061, CHF-2.2.4)")
class DishSubmissionAcceptanceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_dish_us4_acceptance")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
    }

    @Autowired
    private DishService dishService;

    @Autowired
    private AdminNotificationService adminNotificationService;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private AdminNotificationRepository adminNotificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;
    private final UUID chefId = DishTestFixtures.DEFAULT_CHEF_ID;

    private Ingredient rice;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);

        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, role, status, tenant_id, token_version, created_at, updated_at)
                VALUES (?, 'chef.us4@easyeat.com', '$2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2', 'CHEF', 'ACTIVE', ?, 0, NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, chefId, tenantId);

        adminNotificationRepository.deleteAll();
        dishRecipeIngredientRepository.deleteAll();
        dishRepository.deleteAll();
        ingredientRepository.deleteAll();

        rice = new Ingredient(tenantId, "Arroz Carnaroli", IngredientCategory.DRY_GOODS, UnitOfMeasure.G, BigDecimal.valueOf(10));
        rice.setStatus(IngredientStatus.ACTIVE);
        rice = ingredientRepository.save(rice);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Scenario 1: Should block submission when completeness criteria are unmet")
    void scenario1_shouldBlockSubmission_whenIncomplete() {
        // Step 1: Create draft with only identity
        DishDetailResponse draft = dishService.createDraft(
                new CreateDishRequest("Risotto Incompleto", DishCategory.PLATOS_PRINCIPALES, "Sin receta"),
                chefId
        );

        // Attempt submission: fails because no recipe lines and allergens not confirmed
        assertThatThrownBy(() -> dishService.submitForReview(draft.id()))
                .isInstanceOf(IncompleteDishException.class)
                .satisfies(ex -> {
                    IncompleteDishException ide = (IncompleteDishException) ex;
                    assertThat(ide.getMissingConditions()).hasSizeGreaterThanOrEqualTo(2);
                });

        // Status must remain DRAFT
        Dish persisted = dishRepository.findById(draft.id()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(DishStatus.DRAFT);
    }

    @Test
    @DisplayName("Scenario 2 & 3: Successful submission locks dish against modifications and creates admin alert")
    void scenario2And3_successfulSubmission_locksDishAndAlertsAdmin() {
        // Step 1: Create draft
        DishDetailResponse draft = dishService.createDraft(
                new CreateDishRequest("Risotto Completo", DishCategory.PLATOS_PRINCIPALES, "Delicioso risotto"),
                chefId
        );

        // Step 2: Add recipe with active ingredient
        dishService.updateRecipe(draft.id(), new RecipeUpdateRequest(List.of(
                new RecipeIngredientItem(rice.getId(), 250.0)
        )));

        // Step 3: Tag allergens and confirm section
        dishService.updateAllergens(draft.id(), new AllergenUpdateRequest(List.of("CELIAC"), true));

        // Step 4: Submit for review
        DishDetailResponse submitted = dishService.submitForReview(draft.id());

        assertThat(submitted.status()).isEqualTo(DishStatus.PENDING_PRICE);

        // Verify entity persisted in PENDING_PRICE with submittedAt
        Dish dishInDb = dishRepository.findById(draft.id()).orElseThrow();
        assertThat(dishInDb.getStatus()).isEqualTo(DishStatus.PENDING_PRICE);
        assertThat(dishInDb.getSubmittedAt()).isNotNull();

        // Scenario 3: Immutability check - all Chef modifications must fail with DishLockedException
        assertThatThrownBy(() -> dishService.updateBasicInfo(draft.id(), new UpdateDishRequest("Nombre Editado", DishCategory.POSTRES, "Desc")))
                .isInstanceOf(DishLockedException.class);

        assertThatThrownBy(() -> dishService.updateRecipe(draft.id(), new RecipeUpdateRequest(List.of())))
                .isInstanceOf(DishLockedException.class);

        assertThatThrownBy(() -> dishService.updateAllergens(draft.id(), new AllergenUpdateRequest(List.of(), true)))
                .isInstanceOf(DishLockedException.class);

        assertThatThrownBy(() -> dishService.deleteDraft(draft.id()))
                .isInstanceOf(DishLockedException.class);

        // Step 5: Verify Administrator notification created
        List<AdminNotification> notifications = adminNotificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        AdminNotification notif = notifications.get(0);
        assertThat(notif.getDishId()).isEqualTo(draft.id());
        assertThat(notif.getType()).isEqualTo(NotificationType.DISH_SUBMITTED_FOR_REVIEW);
        assertThat(notif.isRead()).isFalse();
        assertThat(notif.getMessage()).contains("Risotto Completo");

        // Scenario 4: Mark notification as read
        NotificationResponse readResponse = adminNotificationService.markAsRead(notif.getId());
        assertThat(readResponse.isRead()).isTrue();

        long unreadCount = adminNotificationService.getUnreadCount();
        assertThat(unreadCount).isEqualTo(0L);
    }
}
