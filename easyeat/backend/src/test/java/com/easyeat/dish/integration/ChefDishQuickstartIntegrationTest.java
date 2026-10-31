package com.easyeat.dish.integration;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.AllergenUpdateRequest;
import com.easyeat.dish.dto.CreateDishRequest;
import com.easyeat.dish.dto.RecipeIngredientItem;
import com.easyeat.dish.dto.RecipeUpdateRequest;
import com.easyeat.dish.dto.UpdateDishRequest;
import com.easyeat.dish.repository.DishPhotoRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.notification.domain.AdminNotification;
import com.easyeat.notification.domain.NotificationType;
import com.easyeat.notification.repository.AdminNotificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test validating Scenarios 1 through 7 of
 * specs/005-chef-dish-management/quickstart.md across HTTP controllers,
 * domain services, and database persistence with PostgreSQL Testcontainers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("ChefDishQuickstartIntegrationTest (T075 - End-to-End Quickstart Scenarios 1 to 7)")
class ChefDishQuickstartIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_chef_dish_quickstart_it")
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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private DishPhotoRepository dishPhotoRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private AdminNotificationRepository adminNotificationRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.easyeat.dish.service.DishService dishService;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;
    private final UUID chefId = DishTestFixtures.DEFAULT_CHEF_ID;
    private final UUID adminId = DishTestFixtures.DEFAULT_ADMIN_ID;

    private User chefUser;
    private User adminUser;
    private String chefToken;
    private String adminToken;

    private Ingredient ojoDeBife;
    private Ingredient papa;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);

        // Clean tables in dependency order
        adminNotificationRepository.deleteAll();
        dishPhotoRepository.deleteAll();
        dishRecipeIngredientRepository.deleteAll();
        dishRepository.deleteAll();
        ingredientRepository.deleteAll();
        userRepository.deleteAll();

        // Create Chef User
        chefUser = new User();
        chefUser.setId(chefId);
        chefUser.setEmail("chef.quickstart@easyeat.com");
        chefUser.setPasswordHash("$2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2");
        chefUser.setRole(UserRole.CHEF);
        chefUser.setStatus(UserStatus.ACTIVE);
        chefUser.setTenantId(tenantId);
        chefUser.setTokenVersion(0);
        chefUser = userRepository.save(chefUser);
        chefToken = jwtService.generateToken(chefUser);

        // Create Admin User
        adminUser = new User();
        adminUser.setId(adminId);
        adminUser.setEmail("admin.quickstart@easyeat.com");
        adminUser.setPasswordHash("$2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2");
        adminUser.setRole(UserRole.ADMINISTRADOR);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setTenantId(tenantId);
        adminUser.setTokenVersion(0);
        adminUser = userRepository.save(adminUser);
        adminToken = jwtService.generateToken(adminUser);

        // Seed Active Ingredients
        ojoDeBife = new Ingredient(tenantId, "Ojo de Bife", IngredientCategory.MEAT, UnitOfMeasure.KG, BigDecimal.valueOf(12000.00));
        ojoDeBife.setStatus(IngredientStatus.ACTIVE);
        ojoDeBife = ingredientRepository.save(ojoDeBife);

        papa = new Ingredient(tenantId, "Papa", IngredientCategory.PRODUCE, UnitOfMeasure.KG, BigDecimal.valueOf(1500.00));
        papa.setStatus(IngredientStatus.ACTIVE);
        papa = ingredientRepository.save(papa);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Complete E2E Quickstart Lifecycle (Scenarios 1 through 7 in quickstart.md)")
    void executeCompleteQuickstartLifecycle() throws Exception {

        // =========================================================================
        // SCENARIO 1: Basic draft creation with photo (CHF-2.2.1)
        // =========================================================================
        CreateDishRequest createRequest = new CreateDishRequest(
                "Ojo de Bife con Papas Rusticas",
                DishCategory.PLATOS_PRINCIPALES,
                "Corte vacuno a las brasas con hierbas aromaticas"
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/dishes")
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name", is("Ojo de Bife con Papas Rusticas")))
                .andExpect(jsonPath("$.category", is("Platos principales")))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID dishId = UUID.fromString(createdJson.get("id").asText());

        // Attach JPEG Photo
        byte[] validJpeg = DishTestFixtures.createValidJpegBytes();
        MockMultipartFile photoFile = new MockMultipartFile(
                "file", "ojodebife.jpg", "image/jpeg", validJpeg
        );

        mockMvc.perform(multipart("/api/v1/dishes/{id}/photo", dishId)
                        .file(photoFile)
                        .header("Authorization", "Bearer " + chefToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl", is("/api/v1/dishes/" + dishId + "/photo")));

        // Query binary photo
        mockMvc.perform(get("/api/v1/dishes/{id}/photo", dishId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(content().bytes(validJpeg));

        // Query draft list
        mockMvc.perform(get("/api/v1/dishes")
                        .param("status", "DRAFT")
                        .header("Authorization", "Bearer " + chefToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + dishId + "')].status").value("DRAFT"));

        // =========================================================================
        // SCENARIO 2: Name uniqueness validation and whitespace trimming (CHF-2.2.1)
        // =========================================================================
        CreateDishRequest duplicateRequest = new CreateDishRequest(
                "  Ojo de Bife con Papas Rusticas   ",
                DishCategory.PLATOS_PRINCIPALES,
                "Intento duplicado con espacios adicionales"
        );

        mockMvc.perform(post("/api/v1/dishes")
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Duplicate Dish Name")));

        // =========================================================================
        // SCENARIO 3: Recipe composition (BOM) and duplicate rejection (CHF-2.2.2)
        // =========================================================================
        // Add two distinct active ingredients
        RecipeUpdateRequest recipeRequest = new RecipeUpdateRequest(List.of(
                new RecipeIngredientItem(ojoDeBife.getId(), 0.400),
                new RecipeIngredientItem(papa.getId(), 0.300)
        ));

        mockMvc.perform(put("/api/v1/dishes/{id}/recipe", dishId)
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recipeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredients", hasSize(2)));

        // Verify duplicate rejection in domain service
        RecipeUpdateRequest duplicateRecipeRequest = new RecipeUpdateRequest(List.of(
                new RecipeIngredientItem(ojoDeBife.getId(), 0.400),
                new RecipeIngredientItem(ojoDeBife.getId(), 0.200)
        ));

        TenantContextHolder.setTenantId(tenantId);
        assertThatThrownBy(() -> dishService.updateRecipe(dishId, duplicateRecipeRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicado");

        // Verify non-positive quantity rejection (quantity <= 0)
        RecipeUpdateRequest invalidQuantityRequest = new RecipeUpdateRequest(List.of(
                new RecipeIngredientItem(ojoDeBife.getId(), 0.0)
        ));

        mockMvc.perform(put("/api/v1/dishes/{id}/recipe", dishId)
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidQuantityRequest)))
                .andExpect(status().isUnprocessableEntity());

        // =========================================================================
        // SCENARIO 4: Allergen declaration and formal confirmation (CHF-2.2.3)
        // =========================================================================
        AllergenUpdateRequest allergenRequest = new AllergenUpdateRequest(
                List.of("CELIAC"),
                true
        );

        mockMvc.perform(put("/api/v1/dishes/{id}/allergens", dishId)
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allergenRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergensConfirmed", is(true)))
                .andExpect(jsonPath("$.restrictionCodes", hasItem("CELIAC")));

        // =========================================================================
        // SCENARIO 5: Submission rejection with incomplete recipe or unconfirmed allergens (CHF-2.2.4)
        // =========================================================================
        CreateDishRequest incompleteDraftRequest = new CreateDishRequest(
                "Plato Incompleto para Rechazo",
                DishCategory.POSTRES,
                "Borrador sin receta ni confirmación"
        );

        MvcResult incompleteResult = mockMvc.perform(post("/api/v1/dishes")
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incompleteDraftRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID incompleteDishId = UUID.fromString(objectMapper.readTree(incompleteResult.getResponse().getContentAsString()).get("id").asText());

        // Attempt submission of incomplete draft -> 422 Unprocessable Entity
        mockMvc.perform(post("/api/v1/dishes/{id}/submit-review", incompleteDishId)
                        .header("Authorization", "Bearer " + chefToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title", is("Incomplete Dish Technical File")));

        // =========================================================================
        // SCENARIO 6: Successful submission and internal administrative alert generation (CHF-2.2.4)
        // =========================================================================
        // Submit the complete dish (dishId)
        mockMvc.perform(post("/api/v1/dishes/{id}/submit-review", dishId)
                        .header("Authorization", "Bearer " + chefToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING_PRICE")));

        // Admin queries unread notifications
        MvcResult notificationsResult = mockMvc.perform(get("/api/v1/admin/notifications")
                        .param("unreadOnly", "true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].type", is("DISH_SUBMITTED_FOR_REVIEW")))
                .andExpect(jsonPath("$.content[0].dishId", is(dishId.toString())))
                .andReturn();

        UUID notificationId = UUID.fromString(
                objectMapper.readTree(notificationsResult.getResponse().getContentAsString())
                        .get("content").get(0).get("id").asText()
        );

        // Admin marks notification as read
        mockMvc.perform(patch("/api/v1/admin/notifications/{id}/read", notificationId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead", is(true)));

        // =========================================================================
        // SCENARIO 7: Edit locking and immutability in "Pending Price" (CHF-2.2.4)
        // =========================================================================
        // 1. Modifying identity/description is rejected with 409 Conflict
        mockMvc.perform(put("/api/v1/dishes/{id}", dishId)
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateDishRequest(
                                "Intento Modificar Nombre",
                                DishCategory.PLATOS_PRINCIPALES,
                                "Nueva descripción rechazada"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Dish Locked")));

        // 2. Deleting locked dish is rejected with 409 Conflict
        mockMvc.perform(delete("/api/v1/dishes/{id}", dishId)
                        .header("Authorization", "Bearer " + chefToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Dish Locked")));

        // 3. Modifying recipe BOM is rejected with 409 Conflict
        mockMvc.perform(put("/api/v1/dishes/{id}/recipe", dishId)
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecipeUpdateRequest(List.of(
                                new RecipeIngredientItem(ojoDeBife.getId(), 0.500)
                        )))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Dish Locked")));

        // 4. Modifying allergens is rejected with 409 Conflict
        mockMvc.perform(put("/api/v1/dishes/{id}/allergens", dishId)
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AllergenUpdateRequest(
                                List.of(), true
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Dish Locked")));

        // 5. Uploading photo is rejected with 409 Conflict
        mockMvc.perform(multipart("/api/v1/dishes/{id}/photo", dishId)
                        .file(photoFile)
                        .header("Authorization", "Bearer " + chefToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Dish Locked")));

        // 6. Deleting photo is rejected with 409 Conflict
        mockMvc.perform(delete("/api/v1/dishes/{id}/photo", dishId)
                        .header("Authorization", "Bearer " + chefToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Dish Locked")));

        // Verify database state: Dish remains PENDING_PRICE and unmutated
        Dish finalDish = dishRepository.findById(dishId).orElseThrow();
        assertThat(finalDish.getStatus()).isEqualTo(DishStatus.PENDING_PRICE);
        assertThat(finalDish.getName()).isEqualTo("Ojo de Bife con Papas Rusticas");
        assertThat(finalDish.getAllergensConfirmed()).isTrue();
    }
}
