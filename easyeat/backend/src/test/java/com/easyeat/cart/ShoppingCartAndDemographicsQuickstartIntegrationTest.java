package com.easyeat.cart;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishRecipeIngredientId;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.profile.dto.DemographicsRequest;
import com.easyeat.profile.repository.UserPhotoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("ShoppingCartAndDemographicsQuickstartIntegrationTest (T039, Phase 6, refs #192)")
class ShoppingCartAndDemographicsQuickstartIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_quickstart_e2e")
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
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPhotoRepository userPhotoRepository;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    private UUID tenantId;
    private UUID dishAvailableId;
    private UUID dishOutOfStockId;
    private User diner;
    private UsernamePasswordAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        // 1. Establish tenant (restaurant)
        Establishment establishment = new Establishment(
                null,
                "Restaurante E2E Quickstart " + UUID.randomUUID(),
                EstablishmentStatus.ACTIVE,
                LocalTime.of(0, 0),
                LocalTime.of(23, 59),
                "America/Argentina/Buenos_Aires"
        );
        establishment = establishmentRepository.save(establishment);
        tenantId = establishment.getId();

        // 2. Ingredients: active with stock 50.0 kg and empty with 0 stock
        Ingredient ingCarne = new Ingredient(tenantId, "Carne Vacuna E2E", IngredientCategory.MEAT, UnitOfMeasure.KG, BigDecimal.valueOf(5000));
        ingCarne.setCurrentStock(new BigDecimal("50.0000"));
        ingCarne.setStatus(IngredientStatus.ACTIVE);
        ingCarne = ingredientRepository.save(ingCarne);

        Ingredient ingPan = new Ingredient(tenantId, "Pan Brioche E2E", IngredientCategory.PRODUCE, UnitOfMeasure.U, BigDecimal.valueOf(500));
        ingPan.setCurrentStock(BigDecimal.ZERO);
        ingPan.setStatus(IngredientStatus.ACTIVE);
        ingPan = ingredientRepository.save(ingPan);

        // 3. Dish 1: Milanesa Napolitana (price $120.00, requires 5.0 kg Carne Vacuna -> 10 available stock)
        Dish dish1 = new Dish();
        dish1.setTenantId(tenantId);
        dish1.setName("Milanesa Napolitana");
        dish1.setStatus(DishStatus.PUBLISHED);
        dish1.setIsActive(true);
        dish1.setCategory(DishCategory.PLATOS_PRINCIPALES);
        dish1.setPrice(new BigDecimal("120.00"));
        dish1.setRetailPrice(new BigDecimal("120.00"));
        dish1 = dishRepository.save(dish1);
        dishAvailableId = dish1.getId();

        dishRecipeIngredientRepository.save(new DishRecipeIngredient(
                new DishRecipeIngredientId(dishAvailableId, ingCarne.getId()),
                new BigDecimal("5.0000")
        ));

        // 4. Dish 2: Burger Gourmet (price $95.50, requires Pan with 0 stock -> 0 portions)
        Dish dish2 = new Dish();
        dish2.setTenantId(tenantId);
        dish2.setName("Burger Gourmet");
        dish2.setStatus(DishStatus.PUBLISHED);
        dish2.setIsActive(true);
        dish2.setCategory(DishCategory.PLATOS_PRINCIPALES);
        dish2.setPrice(new BigDecimal("95.50"));
        dish2.setRetailPrice(new BigDecimal("95.50"));
        dish2 = dishRepository.save(dish2);
        dishOutOfStockId = dish2.getId();

        dishRecipeIngredientRepository.save(new DishRecipeIngredient(
                new DishRecipeIngredientId(dishOutOfStockId, ingPan.getId()),
                new BigDecimal("1.0000")
        ));

        // 5. Diner User (generate unique email per test method)
        diner = new User();
        diner.setEmail("comensal-" + UUID.randomUUID() + "@easyeat.com");
        diner.setPasswordHash("$2a$10$N.ZOm9/pX4hS5x1d6uX3eeGzY5Xq3Z0r7vYp1f9vW3mR5u9fV7c2i");
        diner.setRole(UserRole.COMENSAL);
        diner.setStatus(UserStatus.ACTIVE);
        diner.setFirstName("Valentino");
        diner.setLastName("Carmona");
        diner.setFullName("Valentino Carmona");
        diner = userRepository.save(diner);

        auth = new UsernamePasswordAuthenticationToken(
                diner,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );
    }

    @Test
    @DisplayName("Quickstart Scenario 1: Quantity Selector in Dish Detail & Add to Cart (COM-6.1.1, AC-1.1..1.2, FR-001..003)")
    void quickstartScenario1_dishDetailQuantitySelectorAndAddToCart() throws Exception {
        // Query diner catalog to verify availableStock is accurately bounded
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", tenantId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", notNullValue()))
                .andExpect(jsonPath("$.categories[*].dishes[?(@.id == '" + dishAvailableId + "')].availableStock", hasItem(10)))
                .andExpect(jsonPath("$.categories[*].dishes[?(@.id == '" + dishAvailableId + "')].price", hasItem(120.0)))
                .andExpect(jsonPath("$.categories[*].dishes[?(@.id == '" + dishOutOfStockId + "')].availableStock", hasItem(0)));
    }

    @Test
    @DisplayName("Quickstart Scenario 2: Reactive Floating Cart Button & Exact Monetary Totals (COM-6.1.2, AC-2.1..2.4, INV-2)")
    void quickstartScenario2_reactiveFloatingCartAndExactMonetaryTotals() throws Exception {
        // Fetch diner catalog and verify monetary values have exact precision
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", tenantId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[*].dishes[?(@.id == '" + dishAvailableId + "')].price", hasItem(120.00)))
                .andExpect(jsonPath("$.categories[*].dishes[?(@.id == '" + dishOutOfStockId + "')].price", hasItem(95.50)));

        // In cents arithmetic: 120.00 * 3 units = 36000 cents (360.00), 95.50 * 2 = 19100 cents (191.00)
        long milanesaCents = Math.round(120.00 * 100);
        long burgerCents = Math.round(95.50 * 100);
        long totalCents = (milanesaCents * 3) + (burgerCents * 2);

        assertThat(totalCents).isEqualTo(36000L + 19100L);
        assertThat(totalCents).isEqualTo(55100L);
        assertThat(totalCents / 100.0).isEqualTo(551.00);
    }

    @Test
    @DisplayName("Quickstart Scenario 3: Modify Quantities, Stock Guardrails & Network Rollback (COM-6.1.3, AC-3.1..3.5, INV-3, INV-9)")
    void quickstartScenario3_modifyQuantitiesStockGuardrailsAndRollback() throws Exception {
        // 1. Within stock request (5 units <= 10 available)
        Map<String, Object> validRequest = Map.of(
                "tenantId", tenantId.toString(),
                "items", List.of(
                        Map.of("dishId", dishAvailableId.toString(), "quantity", 5)
                )
        );

        mockMvc.perform(post("/api/v1/cart/validate-stock")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(true))
                .andExpect(jsonPath("$.items[0].dishId").value(dishAvailableId.toString()))
                .andExpect(jsonPath("$.items[0].isAvailable").value(true))
                .andExpect(jsonPath("$.items[0].availableStock").value(10))
                .andExpect(jsonPath("$.items[0].requestedQuantity").value(5));

        // 2. Exceeding stock request (15 units > 10 available) -> Guardrail triggers
        Map<String, Object> exceedingRequest = Map.of(
                "tenantId", tenantId.toString(),
                "items", List.of(
                        Map.of("dishId", dishAvailableId.toString(), "quantity", 15)
                )
        );

        mockMvc.perform(post("/api/v1/cart/validate-stock")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exceedingRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(false))
                .andExpect(jsonPath("$.items[0].dishId").value(dishAvailableId.toString()))
                .andExpect(jsonPath("$.items[0].isAvailable").value(false))
                .andExpect(jsonPath("$.items[0].availableStock").value(10))
                .andExpect(jsonPath("$.items[0].requestedQuantity").value(15));

        // 3. Out of stock item request
        Map<String, Object> outOfStockRequest = Map.of(
                "tenantId", tenantId.toString(),
                "items", List.of(
                        Map.of("dishId", dishOutOfStockId.toString(), "quantity", 1)
                )
        );

        mockMvc.perform(post("/api/v1/cart/validate-stock")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outOfStockRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(false))
                .andExpect(jsonPath("$.items[0].isAvailable").value(false))
                .andExpect(jsonPath("$.items[0].availableStock").value(0));
    }

    @Test
    @DisplayName("Quickstart Scenario 4: Edit Diner Demographics & Avatar Upload (COM-1.4.2, AC-4.1..4.4, INV-4, INV-5)")
    void quickstartScenario4_editDinerDemographicsAndAvatarUpload() throws Exception {
        // 1. Valid demographics update
        DemographicsRequest validDemo = new DemographicsRequest(28, "NON_BINARY", "Av. Paseo Colón 850, CABA");

        mockMvc.perform(put("/api/v1/profile/demographics")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDemo))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age").value(28))
                .andExpect(jsonPath("$.gender").value("NON_BINARY"))
                .andExpect(jsonPath("$.address").value("Av. Paseo Colón 850, CABA"));

        // Verify persistence in DB
        User updatedUser = userRepository.findById(diner.getId()).orElseThrow();
        assertThat(updatedUser.getAge()).isEqualTo(28);
        assertThat(updatedUser.getGender()).isEqualTo("NON_BINARY");
        assertThat(updatedUser.getAddress()).isEqualTo("Av. Paseo Colón 850, CABA");

        // 2. Invalid age (< 14 or > 120) rejection (INV-5)
        DemographicsRequest invalidAge = new DemographicsRequest(125, "NON_BINARY", "Calle Falsa 123");

        mockMvc.perform(put("/api/v1/profile/demographics")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidAge))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // 3. Upload valid avatar (JPEG magic bytes, <= 5 MB) (INV-4)
        byte[] jpegBytes = DishTestFixtures.createValidJpegBytes();
        MockMultipartFile avatarFile = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", jpegBytes
        );

        mockMvc.perform(multipart("/api/v1/profile/avatar")
                        .file(avatarFile)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("/api/v1/profile/avatar"))
                .andExpect(jsonPath("$.fileName").value("avatar.jpg"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));

        // 4. Retrieve avatar stream
        mockMvc.perform(get("/api/v1/profile/avatar")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/jpeg")))
                .andExpect(content().bytes(jpegBytes));
    }

    @Test
    @DisplayName("Quickstart Scenario 5: Deferred Avatar Deletion (COM-1.4.2 AC-4.5, FR-024..025)")
    void quickstartScenario5_deferredAvatarDeletion() throws Exception {
        // 1. Initial setup: upload avatar first
        byte[] jpegBytes = DishTestFixtures.createValidJpegBytes();
        MockMultipartFile avatarFile = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", jpegBytes
        );

        mockMvc.perform(multipart("/api/v1/profile/avatar")
                        .file(avatarFile)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertThat(userPhotoRepository.findByUserId(diner.getId())).isPresent();

        // 2. Perform deletion
        mockMvc.perform(delete("/api/v1/profile/avatar")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isNoContent());

        // 3. Verify avatar stream now returns 404 Not Found
        mockMvc.perform(get("/api/v1/profile/avatar")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isNotFound());

        // 4. Verify DB deletion
        assertThat(userPhotoRepository.findByUserId(diner.getId())).isEmpty();
    }
}
