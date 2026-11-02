package com.easyeat.dish.integration;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishReviewHistory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.domain.ReviewEventType;
import com.easyeat.dish.dto.DishApprovalResponse;
import com.easyeat.dish.dto.DishPricingResponse;
import com.easyeat.dish.dto.DishRejectionResponse;
import com.easyeat.dish.dto.RejectDishRequest;
import com.easyeat.dish.repository.DishPriceHistoryRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.dish.repository.DishReviewHistoryRepository;
import com.easyeat.dish.service.DishGovernanceService;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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

/**
 * T100 (Phase 7): the audit and arithmetic invariants of {@link DishGovernanceService}'s decision
 * transitions that no single unit test proves against a real database and a real clock —
 * {@code cycle_number} across repeated reject/resubmit rounds (FR-022), a repricing of an
 * already-{@code PUBLISHED} dish leaving status, recipe and visibility untouched (FR-011), the
 * margin recomputed after an acquisition-cost change (FR-012), and that pricing, approval and
 * rejection all succeed on a dish whose author Chef has since been deactivated (FR-026).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("DishApprovalPricingIntegrationTest (T100 - Approval/Pricing Audit PostgreSQL Integration Test)")
class DishApprovalPricingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_approval_pricing_it")
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
    private DishGovernanceService governanceService;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DishRecipeIngredientRepository recipeIngredientRepository;

    @Autowired
    private DishReviewHistoryRepository reviewHistoryRepository;

    @Autowired
    private DishPriceHistoryRepository priceHistoryRepository;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;

    private User admin;
    private Ingredient ingredient;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);
        dishRepository.deleteAll();
        userRepository.deleteAll();
        ingredientRepository.deleteAll();

        admin = new User();
        admin.setEmail("admin.approval@easyeat.com");
        admin.setPasswordHash("$2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2");
        admin.setRole(UserRole.ADMINISTRADOR);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setTenantId(tenantId);
        admin.setTokenVersion(0);
        admin = userRepository.save(admin);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));

        ingredient = new Ingredient(tenantId, "Harina", IngredientCategory.DRY_GOODS,
                UnitOfMeasure.KG, new BigDecimal("1000.00"));
        ingredient = ingredientRepository.save(ingredient);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private Dish gateReadyDish(String name, UUID authorId) {
        Dish dish = new Dish(tenantId, name, DishCategory.PLATOS_PRINCIPALES, "Desc", null, true, authorId);
        dish.setStatus(DishStatus.PENDING_PRICE);
        dish.setAllergensConfirmed(true);
        dish = dishRepository.save(dish);
        recipeIngredientRepository.save(new DishRecipeIngredient(dish.getId(), ingredient.getId(),
                new BigDecimal("1.0000")));
        return dish;
    }

    @Test
    @DisplayName("cycle_number increments across repeated reject-then-resubmit rounds, one audit row per cycle")
    void shouldIncrementCycleNumberAcrossRepeatedRounds() {
        Dish dish = gateReadyDish("Empanadas", admin.getId());

        DishRejectionResponse firstRejection = governanceService.reject(dish.getId(), new RejectDishRequest("Falta aclarar el relleno exacto.", null));
        assertThat(firstRejection.cycleNumber()).isEqualTo(1);

        resubmit(dish.getId());
        DishRejectionResponse secondRejection = governanceService.reject(dish.getId(), new RejectDishRequest("El precio sugerido sigue sin cerrar el margen.", null));
        assertThat(secondRejection.cycleNumber()).isEqualTo(2);

        resubmit(dish.getId());
        governanceService.setRetailPrice(dish.getId(), new BigDecimal("3500.00"), null, false);
        DishApprovalResponse approval = governanceService.approve(dish.getId(), false, false);
        assertThat(approval.publishedAt()).isNotNull();

        List<DishReviewHistory> history = reviewHistoryRepository
                .findByDishIdAndTenantIdOrderByCreatedAtAsc(dish.getId(), tenantId);
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getCycleNumber()).isEqualTo(1);
        assertThat(history.get(0).getEventType()).isEqualTo(ReviewEventType.REJECTED);
        assertThat(history.get(1).getCycleNumber()).isEqualTo(2);
        assertThat(history.get(1).getEventType()).isEqualTo(ReviewEventType.REJECTED);
        assertThat(history.get(2).getCycleNumber()).isEqualTo(3);
        assertThat(history.get(2).getEventType()).isEqualTo(ReviewEventType.APPROVED);
    }

    @Test
    @DisplayName("C-02 cancellation is persisted as a terminal soft-delete with an audit cycle")
    void shouldCancelPendingDishAndPersistCancellationEvent() {
        Dish dish = gateReadyDish("Propuesta cancelada", admin.getId());

        governanceService.cancel(dish.getId());

        Dish cancelled = dishRepository.findByIdAndTenantId(dish.getId(), tenantId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(DishStatus.CANCELLED);
        assertThat(cancelled.getIsActive()).isFalse();
        assertThat(dishRepository.findByTenantIdAndStatusAndIsActiveTrue(
                tenantId, DishStatus.CANCELLED)).isEmpty();

        List<DishReviewHistory> history = reviewHistoryRepository
                .findByDishIdAndTenantIdOrderByCreatedAtAsc(dish.getId(), tenantId);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getEventType()).isEqualTo(ReviewEventType.CANCELLED);
        assertThat(history.getFirst().getCycleNumber()).isEqualTo(1);
        assertThat(history.getFirst().getAdminId()).isEqualTo(admin.getId());
        assertThat(history.getFirst().getComment()).isNull();
    }

    @Test
    @DisplayName("Repricing an already-PUBLISHED dish leaves status, recipe and visibility untouched (FR-011)")
    void shouldLeaveStatusRecipeAndVisibilityUntouched_whenRepricingPublishedDish() {
        Dish dish = gateReadyDish("Milanesa Napolitana", admin.getId());
        governanceService.setRetailPrice(dish.getId(), new BigDecimal("5000.00"), null, false);
        governanceService.approve(dish.getId(), false, false);

        long recipeLinesBefore = recipeIngredientRepository.findAll().stream()
                .filter(line -> line.getId().getDishId().equals(dish.getId()))
                .count();

        DishPricingResponse repricing = governanceService.setRetailPrice(
                dish.getId(), new BigDecimal("5800.00"), new BigDecimal("5000.00"), false);

        Dish reloaded = dishRepository.findByIdAndTenantId(dish.getId(), tenantId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DishStatus.PUBLISHED);
        assertThat(reloaded.getIsActive()).isTrue();
        assertThat(reloaded.getRetailPrice()).isEqualByComparingTo("5800.00");

        long recipeLinesAfter = recipeIngredientRepository.findAll().stream()
                .filter(line -> line.getId().getDishId().equals(dish.getId()))
                .count();
        assertThat(recipeLinesAfter).isEqualTo(recipeLinesBefore);

        assertThat(repricing.status()).isEqualTo(DishStatus.PUBLISHED);
        assertThat(priceHistoryRepository.countByDishIdAndTenantId(dish.getId(), tenantId)).isEqualTo(2);
    }

    @Test
    @DisplayName("The margin is recomputed from the Food Cost prevailing at each save, after an acquisition cost change (FR-012)")
    void shouldRecomputeMarginAfterAcquisitionCostChanges() {
        Dish dish = gateReadyDish("Tarta de Verdura", admin.getId());

        DishPricingResponse beforeCostChange = governanceService.setRetailPrice(
                dish.getId(), new BigDecimal("4000.00"), null, false);
        BigDecimal marginBefore = beforeCostChange.margin().marginPct();

        ingredient.setUnitPurchaseCost(new BigDecimal("3000.00"));
        ingredientRepository.save(ingredient);

        DishPricingResponse afterCostChange = governanceService.setRetailPrice(
                dish.getId(), new BigDecimal("4000.00"), new BigDecimal("4000.00"), true);
        BigDecimal marginAfter = afterCostChange.margin().marginPct();

        assertThat(marginAfter).isNotEqualByComparingTo(marginBefore);
        assertThat(afterCostChange.margin().foodCost()).isEqualByComparingTo("3000.0000");
    }

    @Test
    @DisplayName("Pricing, approval and rejection all succeed on a dish whose author Chef has since been deactivated (FR-026)")
    void shouldSucceedGovernanceTransitions_whenAuthorChefDeactivated() {
        User chef = new User();
        chef.setEmail("chef.deactivated@easyeat.com");
        chef.setPasswordHash("$2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2");
        chef.setRole(UserRole.CHEF);
        chef.setStatus(UserStatus.SUSPENDED);
        chef.setTenantId(tenantId);
        chef.setTokenVersion(0);
        chef = userRepository.save(chef);

        Dish approvedPath = gateReadyDish("Sopa de Calabaza", chef.getId());
        governanceService.setRetailPrice(approvedPath.getId(), new BigDecimal("2800.00"), null, false);
        DishApprovalResponse approval = governanceService.approve(approvedPath.getId(), false, false);
        assertThat(approval.status()).isEqualTo(DishStatus.PUBLISHED);

        Dish rejectedPath = gateReadyDish("Pure de Papas", chef.getId());
        DishRejectionResponse rejection = governanceService.reject(rejectedPath.getId(), new RejectDishRequest("Falta especificar la guarnición.", null));
        assertThat(rejection.status()).isEqualTo(DishStatus.DRAFT);
    }

    private void resubmit(UUID dishId) {
        Dish dish = dishRepository.findByIdAndTenantId(dishId, tenantId).orElseThrow();
        dish.setStatus(DishStatus.PENDING_PRICE);
        dishRepository.save(dish);
    }
}
