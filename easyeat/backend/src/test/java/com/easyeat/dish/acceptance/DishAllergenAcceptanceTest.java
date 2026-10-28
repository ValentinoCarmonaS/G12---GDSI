package com.easyeat.dish.acceptance;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.AllergenResponse;
import com.easyeat.dish.dto.AllergenUpdateRequest;
import com.easyeat.dish.dto.DishDetailResponse;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.service.DishService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("User Story 3 Acceptance Test: Allergen and Dietary Labeling (T049, CHF-2.2.3)")
class DishAllergenAcceptanceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_dish_us3_acceptance")
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
    private DishRepository dishRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;
    private final UUID chefId = DishTestFixtures.DEFAULT_CHEF_ID;
    private Dish savedDish;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);

        Dish dish = new Dish();
        dish.setTenantId(tenantId);
        dish.setName("Gazpacho Andaluz " + UUID.randomUUID().toString().substring(0, 8));
        dish.setCategory(DishCategory.ENTRADAS);
        dish.setDescription("Sopa fría tradicional de tomate y verduras");
        dish.setStatus(DishStatus.DRAFT);
        dish.setCreatedBy(chefId);
        dish.setAllergensConfirmed(false);
        dish.setIsActive(true);

        savedDish = dishRepository.save(dish);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Scenario 1 & 2: Chef labels dish with lifestyle and allergen warnings, explicitly confirms, and persists to DB")
    void updateAllergens_shouldPersistToDatabase_whenValidLabelsAndConfirmed() {
        List<String> labels = List.of("VEGAN", "CELIAC");
        AllergenUpdateRequest request = new AllergenUpdateRequest(labels, true);

        AllergenResponse response = dishService.updateAllergens(savedDish.getId(), request);

        assertThat(response.dishId()).isEqualTo(savedDish.getId());
        assertThat(response.allergensConfirmed()).isTrue();
        assertThat(response.restrictionCodes()).containsExactlyInAnyOrder("VEGAN", "CELIAC");

        // Verify direct database persistence in dish_dietary_restrictions table
        Integer joinCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dish_dietary_restrictions WHERE dish_id = ?",
                Integer.class,
                savedDish.getId()
        );
        assertThat(joinCount).isEqualTo(2);

        Boolean confirmedDb = jdbcTemplate.queryForObject(
                "SELECT allergens_confirmed FROM dishes WHERE id = ?",
                Boolean.class,
                savedDish.getId()
        );
        assertThat(confirmedDb).isTrue();

        DishDetailResponse detail = dishService.getDishById(savedDish.getId());
        assertThat(detail.allergensConfirmed()).isTrue();
        assertThat(detail.restrictionCodes()).containsExactlyInAnyOrder("VEGAN", "CELIAC");
    }

    @Test
    @DisplayName("Scenario 2: Zero labels selected is valid when confirmed explicitly")
    void updateAllergens_shouldPersistZeroLabels_whenConfirmedExplicitly() {
        AllergenUpdateRequest request = new AllergenUpdateRequest(Collections.emptyList(), true);

        AllergenResponse response = dishService.updateAllergens(savedDish.getId(), request);

        assertThat(response.allergensConfirmed()).isTrue();
        assertThat(response.restrictionCodes()).isEmpty();

        Integer joinCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dish_dietary_restrictions WHERE dish_id = ?",
                Integer.class,
                savedDish.getId()
        );
        assertThat(joinCount).isEqualTo(0);

        Boolean confirmedDb = jdbcTemplate.queryForObject(
                "SELECT allergens_confirmed FROM dishes WHERE id = ?",
                Boolean.class,
                savedDish.getId()
        );
        assertThat(confirmedDb).isTrue();
    }

    @Test
    @DisplayName("Scenario 3: Allergen section is treated as incomplete if not explicitly confirmed")
    void updateAllergens_shouldRemainUnconfirmed_whenConfirmedFlagFalse() {
        AllergenUpdateRequest request = new AllergenUpdateRequest(List.of("VEGAN"), false);

        AllergenResponse response = dishService.updateAllergens(savedDish.getId(), request);

        assertThat(response.allergensConfirmed()).isFalse();

        DishDetailResponse detail = dishService.getDishById(savedDish.getId());
        assertThat(detail.allergensConfirmed()).isFalse();
        assertThat(detail.missingConditions()).contains("Se debe confirmar la sección de alérgenos");
    }

    @Test
    @DisplayName("Scenario 4: Re-editing previously confirmed allergen section updates labels and retains confirmation")
    void updateAllergens_shouldUpdateLabelsAndRetainConfirmation() {
        dishService.updateAllergens(savedDish.getId(), new AllergenUpdateRequest(List.of("DAIRY"), true));

        // Re-edit with different labels
        AllergenResponse updated = dishService.updateAllergens(
                savedDish.getId(),
                new AllergenUpdateRequest(List.of("VEGAN", "SOY"), true)
        );

        assertThat(updated.allergensConfirmed()).isTrue();
        assertThat(updated.restrictionCodes()).containsExactlyInAnyOrder("VEGAN", "SOY");

        DishDetailResponse detail = dishService.getDishById(savedDish.getId());
        assertThat(detail.restrictionCodes()).containsExactlyInAnyOrder("VEGAN", "SOY");
    }

    @Test
    @DisplayName("Scenario 5: Attempting to modify allergens on dish in PENDING_PRICE is rejected with DishLockedException")
    void updateAllergens_shouldBeRejected_whenDishInPendingPrice() {
        savedDish.setStatus(DishStatus.PENDING_PRICE);
        dishRepository.save(savedDish);

        AllergenUpdateRequest request = new AllergenUpdateRequest(List.of("CELIAC"), true);

        assertThatThrownBy(() -> dishService.updateAllergens(savedDish.getId(), request))
                .isInstanceOf(DishLockedException.class);
    }
}
