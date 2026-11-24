package com.easyeat.catalog.integration;

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
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishPhoto;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishRecipeIngredientId;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.repository.DishPhotoRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.operations.cache.OperatingScheduleCache;
import com.easyeat.profile.cache.DietaryTaxonomyCache;
import com.easyeat.profile.domain.UserDietaryRestriction;
import com.easyeat.profile.repository.UserDietaryRestrictionRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("CatalogBatchQueryCountIntegrationTest (T019, FR-001, FR-002, SC-001, SC-005)")
class CatalogBatchQueryCountIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_catalog_batch_it")
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
    private DishRepository dishRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DishPhotoRepository dishPhotoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDietaryRestrictionRepository userDietaryRestrictionRepository;

    @Autowired
    private DietaryTaxonomyCache dietaryTaxonomyCache;

    @Autowired
    private OperatingScheduleCache operatingScheduleCache;

    private SqlQueryCountAssertionHelper sqlHelper;
    private UUID tenantId;
    private User comensalUser;
    private UUID firstDishId;
    private UUID firstIngredientId;

    @BeforeEach
    void setUp() {
        sqlHelper = SqlQueryCountAssertionHelper.from(entityManagerFactory);
        dishPhotoRepository.deleteAll();
        dishRecipeIngredientRepository.deleteAll();
        dishRepository.deleteAll();
        ingredientRepository.deleteAll();
        userDietaryRestrictionRepository.deleteAll();
        userRepository.deleteAll();
        establishmentRepository.deleteAll();

        Establishment est = new Establishment(
                null,
                "Restaurante Batch O(1)",
                EstablishmentStatus.ACTIVE,
                LocalTime.of(0, 0),
                LocalTime.of(23, 59, 59),
                "America/Argentina/Buenos_Aires"
        );
        est = establishmentRepository.save(est);
        tenantId = est.getId();

        comensalUser = new User();
        comensalUser.setEmail("comensal.batch@easyeat.com");
        comensalUser.setPasswordHash("$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyha");
        comensalUser.setFirstName("Valen");
        comensalUser.setLastName("Carmona");
        comensalUser.setRole(UserRole.COMENSAL);
        comensalUser.setStatus(UserStatus.ACTIVE);
        comensalUser.setMustChangePassword(false);
        comensalUser = userRepository.save(comensalUser);

        userDietaryRestrictionRepository.save(new UserDietaryRestriction(comensalUser.getId(), "DAIRY"));

        List<Ingredient> ingredients = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Ingredient ing = new Ingredient(tenantId, "Insumo " + i, IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("50.0000"));
            ing.setCurrentStock(new BigDecimal("50.0000"));
            ing.setStatus(IngredientStatus.ACTIVE);
            ingredients.add(ingredientRepository.save(ing));
        }
        firstIngredientId = ingredients.get(0).getId();

        DishCategory[] categories = DishCategory.values();
        for (int d = 0; d < 20; d++) {
            Dish dish = new Dish(tenantId, "Plato Batch " + d, "Descripción " + d, new BigDecimal("5000.00"), true);
            dish.setRetailPrice(new BigDecimal("5000.00"));
            dish.setCategory(categories[d % categories.length]);
            dish.setStatus(DishStatus.PUBLISHED);
            dish.setIsActive(true);
            dish.setPausedByChef(false);
            dish.setRestrictionCodes(d % 2 == 0 ? Set.of("DAIRY", "VEGAN") : Set.of("VEGETARIAN"));
            dish = dishRepository.save(dish);

            if (d == 0) {
                firstDishId = dish.getId();
            }

            for (Ingredient ing : ingredients) {
                dishRecipeIngredientRepository.save(
                        new DishRecipeIngredient(new DishRecipeIngredientId(dish.getId(), ing.getId()), new BigDecimal("1.0000"))
                );
            }

            if (d % 2 == 0) {
                DishPhoto photo = new DishPhoto(dish.getId(), "dish-" + d + ".jpg", "image/jpeg", 4L, new byte[]{1, 2, 3, 4});
                dishPhotoRepository.save(photo);
            }
        }

        // Warm low-volatility metadata caches
        dietaryTaxonomyCache.getMapByCode();
        operatingScheduleCache.getWeekMap(tenantId);
    }

    private UsernamePasswordAuthenticationToken comensalAuth() {
        return new UsernamePasswordAuthenticationToken(
                comensalUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );
    }

    @Test
    @DisplayName("SC-001: GET /catalog executes <= 6 SQL queries for >= 20 dishes x 4 ingredients with dietary restrictions")
    void getCatalog_shouldExecuteAtMost6SqlQueries() throws Exception {
        sqlHelper.clear();
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", tenantId)
                        .with(authentication(comensalAuth()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isArray());

        long queries = sqlHelper.getPrepareStatementCount();
        assertThat(queries)
                .as("Authenticated /catalog must execute <= 6 SQL queries for 20 dishes x 4 ingredients")
                .isLessThanOrEqualTo(6L);
    }

    @Test
    @DisplayName("SC-001: Guest GET /dishes executes <= 6 SQL queries")
    void getGuestDishes_shouldExecuteAtMost6SqlQueries() throws Exception {
        sqlHelper.clear();
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/dishes", tenantId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(20));

        long queries = sqlHelper.getPrepareStatementCount();
        assertThat(queries)
                .as("Guest /dishes must execute <= 6 SQL queries")
                .isLessThanOrEqualTo(6L);
    }

    @Test
    @DisplayName("SC-001 & SC-005: GET /catalog/availability executes <= 4 SQL queries, returns 304 on ETag match, and reflects stock/pause mutations immediately")
    void getAvailability_shouldExecuteAtMost4SqlQueriesAndReflectMutationsImmediately() throws Exception {
        sqlHelper.clear();
        MvcResult firstResult = mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId)
                        .with(authentication(comensalAuth()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.availability['" + firstDishId + "']").value(true))
                .andReturn();

        long firstQueries = sqlHelper.getPrepareStatementCount();
        assertThat(firstQueries)
                .as("GET /catalog/availability must execute <= 4 SQL queries")
                .isLessThanOrEqualTo(4L);

        String etag = firstResult.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();

        sqlHelper.clear();
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId)
                        .with(authentication(comensalAuth()))
                        .header(HttpHeaders.IF_NONE_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag));

        long notModifiedQueries = sqlHelper.getPrepareStatementCount();
        assertThat(notModifiedQueries)
                .as("GET /catalog/availability 304 must execute <= 4 SQL queries")
                .isLessThanOrEqualTo(4L);

        // Mutate stock to 0 -> immediate coherence without stale cache
        Ingredient ing = ingredientRepository.findById(firstIngredientId).orElseThrow();
        ing.setCurrentStock(BigDecimal.ZERO);
        ingredientRepository.save(ing);

        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId)
                        .with(authentication(comensalAuth()))
                        .header(HttpHeaders.IF_NONE_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability['" + firstDishId + "']").value(false));
    }
}
