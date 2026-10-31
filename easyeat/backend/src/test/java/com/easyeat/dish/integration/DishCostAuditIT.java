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
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.repository.DishPhotoRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.notification.repository.AdminNotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("DishCostAuditIT (T010 - Food Cost Audit PostgreSQL Integration Test)")
class DishCostAuditIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_cost_audit_it")
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

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;
    private final UUID adminId = DishTestFixtures.DEFAULT_ADMIN_ID;

    private User adminUser;
    private String adminToken;

    private User otherTenantAdmin;
    private String otherTenantToken;

    private Ingredient carne;
    private Ingredient papa;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);

        adminNotificationRepository.deleteAll();
        dishPhotoRepository.deleteAll();
        dishRecipeIngredientRepository.deleteAll();
        dishRepository.deleteAll();
        ingredientRepository.deleteAll();
        userRepository.deleteAll();

        // Create Admin in tenantId
        adminUser = new User();
        adminUser.setId(adminId);
        adminUser.setEmail("admin.audit@easyeat.com");
        adminUser.setPasswordHash("$2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2");
        adminUser.setRole(UserRole.ADMINISTRADOR);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setTenantId(tenantId);
        adminUser.setTokenVersion(0);
        adminUser = userRepository.save(adminUser);
        adminToken = jwtService.generateToken(adminUser);

        // Create Admin in another tenant
        UUID otherTenantId = UUID.randomUUID();
        otherTenantAdmin = new User();
        otherTenantAdmin.setId(UUID.randomUUID());
        otherTenantAdmin.setEmail("other.admin@easyeat.com");
        otherTenantAdmin.setPasswordHash("$2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2");
        otherTenantAdmin.setRole(UserRole.ADMINISTRADOR);
        otherTenantAdmin.setStatus(UserStatus.ACTIVE);
        otherTenantAdmin.setTenantId(otherTenantId);
        otherTenantAdmin.setTokenVersion(0);
        otherTenantAdmin = userRepository.save(otherTenantAdmin);
        otherTenantToken = jwtService.generateToken(otherTenantAdmin);

        // Create Ingredients
        carne = new Ingredient(tenantId, "Lomo", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("10000.00"));
        carne.setStatus(IngredientStatus.ACTIVE);
        carne = ingredientRepository.save(carne);

        papa = new Ingredient(tenantId, "Papas", IngredientCategory.PRODUCE, UnitOfMeasure.KG, new BigDecimal("1200.00"));
        papa.setStatus(IngredientStatus.ACTIVE);
        papa = ingredientRepository.save(papa);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Successfully computes food cost audit with live arithmetic and dietary labels in PENDING_PRICE")
    void shouldAuditFoodCost_whenPendingPriceWithRecipe() throws Exception {
        Dish dish = new Dish(tenantId, "Bife con Papas", DishCategory.PLATOS_PRINCIPALES, "Delicioso lomo", null, true, adminUser.getId());
        dish.setStatus(DishStatus.PENDING_PRICE);
        dish.setRestrictionCodes(Set.of("CELIAC"));
        dish = dishRepository.save(dish);

        // 0.3000 kg carne (3000.00) + 0.5000 kg papa (600.00) = 3600.00
        DishRecipeIngredient r1 = new DishRecipeIngredient(dish.getId(), carne.getId(), new BigDecimal("0.3000"));
        DishRecipeIngredient r2 = new DishRecipeIngredient(dish.getId(), papa.getId(), new BigDecimal("0.5000"));
        dishRecipeIngredientRepository.saveAll(List.of(r1, r2));

        mockMvc.perform(get("/api/v1/dishes/{id}/cost-audit", dish.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dishId").value(dish.getId().toString()))
                .andExpect(jsonPath("$.dishName").value("Bife con Papas"))
                .andExpect(jsonPath("$.totalFoodCost").value(3600.00))
                .andExpect(jsonPath("$.hasUnpricedIngredients").value(false))
                .andExpect(jsonPath("$.requiredDietaryRestrictions[0]").value("CELIAC"))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    @DisplayName("Dynamically reflects live inventory price updates upon next audit query (SC-005)")
    void shouldReflectPriceUpdatesDynamically() throws Exception {
        Dish dish = new Dish(tenantId, "Lomo Simple", DishCategory.PLATOS_PRINCIPALES, "Lomo", null, true, adminUser.getId());
        dish.setStatus(DishStatus.PENDING_PRICE);
        dish = dishRepository.save(dish);

        DishRecipeIngredient r1 = new DishRecipeIngredient(dish.getId(), carne.getId(), new BigDecimal("0.2500"));
        dishRecipeIngredientRepository.save(r1);

        // Initial cost: 0.25 * 10000 = 2500.00
        mockMvc.perform(get("/api/v1/dishes/{id}/cost-audit", dish.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFoodCost").value(2500.00));

        // Update ingredient purchase cost: 10000 -> 14000
        carne.setUnitPurchaseCost(new BigDecimal("14000.00"));
        ingredientRepository.save(carne);

        // Re-query: 0.25 * 14000 = 3500.00
        mockMvc.perform(get("/api/v1/dishes/{id}/cost-audit", dish.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFoodCost").value(3500.00));
    }

    @Test
    @DisplayName("Returns 409 Conflict when dish is in DRAFT status")
    void shouldReturn409_whenDishInDraft() throws Exception {
        Dish dish = new Dish(tenantId, "Plato Borrador", DishCategory.PLATOS_PRINCIPALES, "Desc", null, false, adminUser.getId());
        dish.setStatus(DishStatus.DRAFT);
        dish = dishRepository.save(dish);

        DishRecipeIngredient r1 = new DishRecipeIngredient(dish.getId(), carne.getId(), new BigDecimal("0.2000"));
        dishRecipeIngredientRepository.save(r1);

        mockMvc.perform(get("/api/v1/dishes/{id}/cost-audit", dish.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Returns 400 Bad Request when dish has an empty recipe")
    void shouldReturn400_whenDishHasNoIngredients() throws Exception {
        Dish dish = new Dish(tenantId, "Plato Vacio", DishCategory.PLATOS_PRINCIPALES, "Desc", null, false, adminUser.getId());
        dish.setStatus(DishStatus.PENDING_PRICE);
        dish = dishRepository.save(dish);

        mockMvc.perform(get("/api/v1/dishes/{id}/cost-audit", dish.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Returns 404 Not Found when cross-tenant access is attempted")
    void shouldReturn404_whenCrossTenantAccess() throws Exception {
        Dish dish = new Dish(tenantId, "Plato Tenant A", DishCategory.PLATOS_PRINCIPALES, "Desc", null, false, adminUser.getId());
        dish.setStatus(DishStatus.PENDING_PRICE);
        dish = dishRepository.save(dish);

        mockMvc.perform(get("/api/v1/dishes/{id}/cost-audit", dish.getId())
                        .header("Authorization", "Bearer " + otherTenantToken))
                .andExpect(status().isNotFound());
    }
}
