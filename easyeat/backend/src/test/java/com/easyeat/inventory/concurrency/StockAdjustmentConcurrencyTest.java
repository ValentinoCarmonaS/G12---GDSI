package com.easyeat.inventory.concurrency;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.StockAdjustmentMode;
import com.easyeat.inventory.dto.StockAdjustmentRequest;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.exception.NegativeStockException;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.inventory.repository.StockAuditEntryRepository;
import com.easyeat.inventory.service.StockAdjustmentService;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class StockAdjustmentConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(StockAdjustmentConcurrencyTest.class);

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
    private IngredientRepository ingredientRepository;

    @Autowired
    private StockAuditEntryRepository stockAuditEntryRepository;

    @Autowired
    private StockAdjustmentService stockAdjustmentService;

    @Autowired
    private UserRepository userRepository;

    private UUID tenantId;
    private User adminUser;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);

        adminUser = new User("admin-" + UUID.randomUUID() + "@easyeat.com", "Password123!", UserRole.ADMINISTRADOR);
        adminUser.setTenantId(tenantId);
        adminUser.setFullName("Admin Concurrency");
        adminUser = userRepository.save(adminUser);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminUser, null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Simultaneous deductions with pessimistic locking serialize updates and strictly prevent negative stock")
    void testConcurrentDeductionsPreventNegativeStock() throws Exception {
        Ingredient ingredient = new Ingredient(tenantId, "Olive Oil", IngredientCategory.DRY_GOODS, UnitOfMeasure.L, new BigDecimal("80.0000"));
        ingredient.setCurrentStock(new BigDecimal("10.0000"));
        ingredient = ingredientRepository.save(ingredient);
        UUID ingredientId = ingredient.getId();

        int totalRequests = 15;
        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch readyLatch = new CountDownLatch(totalRequests);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger negativeStockRejectedCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    TenantContextHolder.setTenantId(tenantId);
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(adminUser, null, List.of())
                    );

                    StockAdjustmentRequest request = new StockAdjustmentRequest(
                            StockAdjustmentMode.RELATIVE,
                            new BigDecimal("-1.0000"),
                            "Concurrent deduction #" + index
                    );

                    stockAdjustmentService.adjustStock(ingredientId, request);
                    successCount.incrementAndGet();
                } catch (NegativeStockException e) {
                    negativeStockRejectedCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Deduction thread error: ", e);
                    unexpectedErrorCount.incrementAndGet();
                } finally {
                    TenantContextHolder.clear();
                    SecurityContextHolder.clearContext();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        executor.shutdown();
        boolean finished = executor.awaitTermination(20, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        assertThat(unexpectedErrorCount.get()).isZero();
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(negativeStockRejectedCount.get()).isEqualTo(5);

        TenantContextHolder.setTenantId(tenantId);
        Ingredient updatedIngredient = ingredientRepository.findByIdAndTenantId(ingredientId, tenantId).orElseThrow();
        assertThat(updatedIngredient.getCurrentStock().compareTo(BigDecimal.ZERO)).isZero();

        var auditEntries = stockAuditEntryRepository.findAllByTenantIdAndIngredientIdOrderByCreatedAtDesc(tenantId, ingredientId);
        assertThat(auditEntries).hasSize(10);
    }

    @Test
    @DisplayName("Simultaneous relative increments serialize correctly with zero lost updates")
    void testConcurrentIncrementsZeroLostUpdates() throws Exception {
        Ingredient ingredient = new Ingredient(tenantId, "Salt", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("10.0000"));
        ingredient.setCurrentStock(BigDecimal.ZERO);
        ingredient = ingredientRepository.save(ingredient);
        UUID ingredientId = ingredient.getId();

        int totalRequests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch readyLatch = new CountDownLatch(totalRequests);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    TenantContextHolder.setTenantId(tenantId);
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(adminUser, null, List.of())
                    );

                    StockAdjustmentRequest request = new StockAdjustmentRequest(
                            StockAdjustmentMode.RELATIVE,
                            new BigDecimal("2.5000"),
                            "Concurrent restock #" + index
                    );

                    stockAdjustmentService.adjustStock(ingredientId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Increment thread error: ", e);
                } finally {
                    TenantContextHolder.clear();
                    SecurityContextHolder.clearContext();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        executor.shutdown();
        boolean finished = executor.awaitTermination(20, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        assertThat(successCount.get()).isEqualTo(10);

        TenantContextHolder.setTenantId(tenantId);
        Ingredient updatedIngredient = ingredientRepository.findByIdAndTenantId(ingredientId, tenantId).orElseThrow();
        assertThat(updatedIngredient.getCurrentStock().compareTo(new BigDecimal("25.0000"))).isZero();

        var auditEntries = stockAuditEntryRepository.findAllByTenantIdAndIngredientIdOrderByCreatedAtDesc(tenantId, ingredientId);
        assertThat(auditEntries).hasSize(10);
    }
}
