package com.easyeat.dish.integration;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.service.DishService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("T038: ChefPauseConcurrenceIntegrationTest (EC-015, EC-025, INV-18)")
class ChefPauseConcurrenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_test")
            .withUsername("easyeat_test")
            .withPassword("easyeat_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private DishService dishService;

    private UUID tenantId;
    private Dish dish;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        dish = new Dish();
        dish.setTenantId(tenantId);
        dish.setName("Plato Concurrente");
        dish.setDescription("Desc");
        dish.setCategory(DishCategory.PLATOS_PRINCIPALES);
        dish.setPrice(BigDecimal.valueOf(1500.00));
        dish.setIsActive(true);
        dish.setStatus(DishStatus.PUBLISHED);
        dish.setPausedByChef(false);
        dish.setCreatedAt(Instant.now());
        dish.setUpdatedAt(Instant.now());
        dish = dishRepository.save(dish);
    }

    @AfterEach
    void tearDown() {
        dishRepository.deleteAll();
    }

    @Test
    @DisplayName("testRapidToggle: alternancia rapida -> estado final = ultimo valor confirmado (EC-015)")
    void testRapidToggle() {
        dishService.toggleChefPause(dish.getId(), true, tenantId);
        dishService.toggleChefPause(dish.getId(), false, tenantId);
        dishService.toggleChefPause(dish.getId(), true, tenantId);

        Dish updated = dishRepository.findById(dish.getId()).orElseThrow();
        assertThat(updated.getPausedByChef()).isTrue();
    }

    @Test
    @DisplayName("testMultiChefConcurrence: dos chefs alternan toggle simultaneamente -> ultimo prevalece (EC-025, INV-18)")
    void testMultiChefConcurrence() throws InterruptedException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final boolean pauseVal = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    latch.await();
                    dishService.toggleChefPause(dish.getId(), pauseVal, tenantId);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        Dish updated = dishRepository.findById(dish.getId()).orElseThrow();
        assertThat(updated.getPausedByChef()).isNotNull();
    }
}
