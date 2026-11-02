package com.easyeat.cart;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("CartValidationIntegrationTest (T031, US3, COM-6.1.3, INV-3, INV-9)")
class CartValidationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_cart_it")
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
    private UUID dishNoRecipeId;
    private UsernamePasswordAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        // 1. Establish tenant
        Establishment establishment = new Establishment(
                null,
                "Restaurante Cart Test",
                EstablishmentStatus.ACTIVE,
                LocalTime.of(0, 0),
                LocalTime.of(23, 59),
                "America/Argentina/Buenos_Aires"
        );
        establishment = establishmentRepository.save(establishment);
        tenantId = establishment.getId();

        // 2. Ingredients: One with 50.0 stock, one with 0.0 stock
        Ingredient ingActive = new Ingredient(tenantId, "Carne Vacuna", IngredientCategory.MEAT, UnitOfMeasure.KG, BigDecimal.valueOf(5000));
        ingActive.setCurrentStock(new BigDecimal("50.0000")); // 50 kg
        ingActive.setStatus(IngredientStatus.ACTIVE);
        ingActive = ingredientRepository.save(ingActive);

        Ingredient ingEmpty = new Ingredient(tenantId, "Pan Brioche", IngredientCategory.PRODUCE, UnitOfMeasure.UNITS, BigDecimal.valueOf(500));
        ingEmpty.setCurrentStock(BigDecimal.ZERO);
        ingEmpty.setStatus(IngredientStatus.ACTIVE);
        ingEmpty = ingredientRepository.save(ingEmpty);

        // 3. Dish 1: Milanesa (requires 5.0 kg Carne Vacuna -> 50 / 5 = 10 portions max)
        Dish dish1 = new Dish();
        dish1.setTenantId(tenantId);
        dish1.setName("Milanesa Napolitana");
        dish1.setStatus(DishStatus.PUBLISHED);
        dish1.setIsActive(true);
        dish1.setCategory(DishCategory.PLATOS_PRINCIPALES);
        dish1.setPrice(BigDecimal.valueOf(12000));
        dish1 = dishRepository.save(dish1);
        dishAvailableId = dish1.getId();

        dishRecipeIngredientRepository.save(new DishRecipeIngredient(
                new DishRecipeIngredientId(dishAvailableId, ingActive.getId()),
                new BigDecimal("5.0000")
        ));

        // 4. Dish 2: Burger (requires Pan Brioche with 0 stock -> 0 portions)
        Dish dish2 = new Dish();
        dish2.setTenantId(tenantId);
        dish2.setName("Burger Gourmet");
        dish2.setStatus(DishStatus.PUBLISHED);
        dish2.setIsActive(true);
        dish2.setCategory(DishCategory.PLATOS_PRINCIPALES);
        dish2.setPrice(BigDecimal.valueOf(10000));
        dish2 = dishRepository.save(dish2);
        dishOutOfStockId = dish2.getId();

        dishRecipeIngredientRepository.save(new DishRecipeIngredient(
                new DishRecipeIngredientId(dishOutOfStockId, ingEmpty.getId()),
                new BigDecimal("1.0000")
        ));

        // 5. Dish 3: No recipe (0 portions)
        Dish dish3 = new Dish();
        dish3.setTenantId(tenantId);
        dish3.setName("Ensalada Misteriosa");
        dish3.setStatus(DishStatus.PUBLISHED);
        dish3.setIsActive(true);
        dish3.setCategory(DishCategory.PLATOS_PRINCIPALES);
        dish3.setPrice(BigDecimal.valueOf(8000));
        dish3 = dishRepository.save(dish3);
        dishNoRecipeId = dish3.getId();

        // 6. User auth
        User diner = new User();
        diner.setId(UUID.randomUUID());
        diner.setEmail("diner.cart.validation@easyeat.com");
        diner.setRole(UserRole.COMENSAL);
        diner.setStatus(UserStatus.ACTIVE);

        auth = new UsernamePasswordAuthenticationToken(
                diner,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );
    }

    @Test
    @DisplayName("POST /api/v1/cart/validate-stock returns 200 with isValid: true when all items within stock")
    void validateStock_withinStock_returnsValidTrue() throws Exception {
        Map<String, Object> request = Map.of(
                "tenantId", tenantId.toString(),
                "items", List.of(
                        Map.of("dishId", dishAvailableId.toString(), "quantity", 5)
                )
        );

        mockMvc.perform(post("/api/v1/cart/validate-stock")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(true))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].dishId").value(dishAvailableId.toString()))
                .andExpect(jsonPath("$.items[0].isAvailable").value(true))
                .andExpect(jsonPath("$.items[0].availableStock").value(10))
                .andExpect(jsonPath("$.items[0].requestedQuantity").value(5));
    }

    @Test
    @DisplayName("POST /api/v1/cart/validate-stock returns 200 with isValid: false when requested quantity exceeds stock (INV-3)")
    void validateStock_exceedsStock_returnsValidFalse() throws Exception {
        Map<String, Object> request = Map.of(
                "tenantId", tenantId.toString(),
                "items", List.of(
                        Map.of("dishId", dishAvailableId.toString(), "quantity", 15) // available is 10
                )
        );

        mockMvc.perform(post("/api/v1/cart/validate-stock")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(false))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].dishId").value(dishAvailableId.toString()))
                .andExpect(jsonPath("$.items[0].isAvailable").value(false))
                .andExpect(jsonPath("$.items[0].availableStock").value(10))
                .andExpect(jsonPath("$.items[0].requestedQuantity").value(15));
    }

    @Test
    @DisplayName("POST /api/v1/cart/validate-stock evaluates zero stock and dish with no recipe as availableStock: 0")
    void validateStock_zeroStockAndNoRecipe_returnsAvailableStockZero() throws Exception {
        Map<String, Object> request = Map.of(
                "tenantId", tenantId.toString(),
                "items", List.of(
                        Map.of("dishId", dishOutOfStockId.toString(), "quantity", 1),
                        Map.of("dishId", dishNoRecipeId.toString(), "quantity", 2)
                )
        );

        mockMvc.perform(post("/api/v1/cart/validate-stock")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(false))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].isAvailable").value(false))
                .andExpect(jsonPath("$.items[0].availableStock").value(0))
                .andExpect(jsonPath("$.items[1].isAvailable").value(false))
                .andExpect(jsonPath("$.items[1].availableStock").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/cart/validate-stock without auth returns 401 Unauthorized")
    void validateStock_unauthenticated_returns401() throws Exception {
        Map<String, Object> request = Map.of(
                "tenantId", tenantId.toString(),
                "items", List.of(
                        Map.of("dishId", dishAvailableId.toString(), "quantity", 1)
                )
        );

        mockMvc.perform(post("/api/v1/cart/validate-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
