package com.easyeat.dish.concurrency;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.RejectDishRequest;
import com.easyeat.dish.exception.DishAlreadyDecidedException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T101 (Phase 7, FR-025): two genuinely competing decisions on the same dish, from two threads
 * synchronised with a {@link CountDownLatch} the way {@code StockAdjustmentConcurrencyTest}
 * already proves works against this codebase's real Postgres and real transactions — exactly one
 * commit, a 409 ({@link DishAlreadyDecidedException}) for the loser, and exactly one audit row,
 * regardless of which thread happens to win the race.
 */
@SpringBootTest
@ActiveProfiles("test")
class DishDecisionConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(DishDecisionConcurrencyTest.class);

    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres;

    static {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("easyeat_test")
                    .withUsername("test")
                    .withPassword("test");
            postgres.start();
        } catch (Throwable t) {
            log.warn("Testcontainers PostgreSQL could not start, falling back to container network datasource: {}", t.getMessage());
            postgres = null;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres != null && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        } else {
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://postgres:5432/easyeat");
            registry.add("spring.datasource.username", () -> "easyeat");
            registry.add("spring.datasource.password", () -> "easyeat_dev_password");
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }
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

    private UUID tenantId;
    private User admin;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);

        admin = new User("admin-" + UUID.randomUUID() + "@easyeat.com", "Password123!", UserRole.ADMINISTRADOR);
        admin.setTenantId(tenantId);
        admin.setFullName("Admin Concurrency");
        admin.setStatus(UserStatus.ACTIVE);
        admin = userRepository.save(admin);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private Dish gateReadyDish(String name) {
        Ingredient ingredient = new Ingredient(tenantId, "Arroz", IngredientCategory.DRY_GOODS,
                UnitOfMeasure.KG, new BigDecimal("900.00"));
        ingredient = ingredientRepository.save(ingredient);

        Dish dish = new Dish(tenantId, name, DishCategory.PLATOS_PRINCIPALES, "Desc", null, true, admin.getId());
        dish.setStatus(DishStatus.PENDING_PRICE);
        dish.setAllergensConfirmed(true);
        dish.setRetailPrice(new BigDecimal("3000.00"));
        dish = dishRepository.save(dish);
        recipeIngredientRepository.save(new DishRecipeIngredient(dish.getId(), ingredient.getId(),
                new BigDecimal("1.0000")));
        return dish;
    }

    @Test
    @DisplayName("Two concurrent rejections on the same PENDING_PRICE dish: exactly one commits, the other gets 409, exactly one audit row")
    void testConcurrentRejectionsOnSameDish() throws Exception {
        Dish dish = gateReadyDish("Ñoquis de Papa");
        UUID dishId = dish.getId();

        runConcurrently(
                () -> governanceService.reject(dishId, new RejectDishRequest("Falta especificar el tipo de salsa.", null)),
                () -> governanceService.reject(dishId, new RejectDishRequest("El precio sugerido no cierra el margen.", null))
        );

        Dish reloaded = dishRepository.findByIdAndTenantId(dishId, tenantId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DishStatus.DRAFT);
        assertThat(reviewHistoryRepository.countByDishIdAndTenantId(dishId, tenantId)).isEqualTo(1);
    }

    @Test
    @DisplayName("A concurrent approve and reject on the same PENDING_PRICE dish: exactly one commits, the other gets 409, exactly one audit row")
    void testConcurrentApproveAndRejectOnSameDish() throws Exception {
        Dish dish = gateReadyDish("Tarta de Acelga");
        UUID dishId = dish.getId();

        runConcurrently(
                () -> governanceService.approve(dishId, false, false),
                () -> governanceService.reject(dishId, new RejectDishRequest("Falta la ficha de alérgenos actualizada.", null))
        );

        Dish reloaded = dishRepository.findByIdAndTenantId(dishId, tenantId).orElseThrow();
        assertThat(reloaded.getStatus()).isIn(DishStatus.PUBLISHED, DishStatus.DRAFT);
        assertThat(reviewHistoryRepository.countByDishIdAndTenantId(dishId, tenantId)).isEqualTo(1);
    }

    /**
     * Runs both actions from two threads released by a single latch, each carrying its own
     * tenant and security context (thread-locals do not cross threads), and asserts the
     * invariant that holds regardless of which one wins: exactly one succeeds, the other raises
     * {@link DishAlreadyDecidedException}.
     */
    private void runConcurrently(ThrowingRunnable actionA, ThrowingRunnable actionB) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyDecidedCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        executor.submit(runnerFor(actionA, readyLatch, startLatch, successCount, alreadyDecidedCount,
                unexpectedErrorCount, unexpected));
        executor.submit(runnerFor(actionB, readyLatch, startLatch, successCount, alreadyDecidedCount,
                unexpectedErrorCount, unexpected));

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        executor.shutdown();
        boolean finished = executor.awaitTermination(20, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        if (unexpected.get() != null) {
            throw new AssertionError("Unexpected error in a concurrency thread", unexpected.get());
        }
        assertThat(unexpectedErrorCount.get()).isZero();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(alreadyDecidedCount.get()).isEqualTo(1);
    }

    private Runnable runnerFor(ThrowingRunnable action, CountDownLatch readyLatch, CountDownLatch startLatch,
                               AtomicInteger successCount, AtomicInteger alreadyDecidedCount,
                               AtomicInteger unexpectedErrorCount, AtomicReference<Throwable> unexpected) {
        return () -> {
            readyLatch.countDown();
            try {
                startLatch.await();

                TenantContextHolder.setTenantId(tenantId);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(admin, null, List.of()));

                action.run();
                successCount.incrementAndGet();
            } catch (DishAlreadyDecidedException e) {
                alreadyDecidedCount.incrementAndGet();
            } catch (Exception e) {
                log.error("Decision thread error: ", e);
                unexpectedErrorCount.incrementAndGet();
                unexpected.set(e);
            } finally {
                TenantContextHolder.clear();
                SecurityContextHolder.clearContext();
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
