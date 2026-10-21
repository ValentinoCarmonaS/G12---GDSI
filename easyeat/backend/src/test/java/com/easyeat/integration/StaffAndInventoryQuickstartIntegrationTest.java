package com.easyeat.integration;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.dto.LoginRequest;
import com.easyeat.staff.dto.StaffRegistrationRequest;
import com.easyeat.staff.dto.StaffRegistrationResponse;
import com.easyeat.staff.dto.StaffRole;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.inventory.dto.*;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.inventory.repository.StockAuditEntryRepository;
import com.easyeat.inventory.service.StockAdjustmentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffAndInventoryQuickstartIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(StaffAndInventoryQuickstartIntegrationTest.class);

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
            log.warn("Testcontainers could not start: {}", t.getMessage());
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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private StockAdjustmentService stockAdjustmentService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private UUID tenantId;
    private User adminUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);

        adminUser = new User("admin-" + UUID.randomUUID() + "@easyeat.com", passwordEncoder.encode("AdminPass123!"), UserRole.ADMINISTRADOR);
        adminUser.setTenantId(tenantId);
        adminUser.setFullName("Super Admin");
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser = userRepository.save(adminUser);

        adminToken = jwtService.generateToken(adminUser);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Scenario 1: Staff Registration and One-Time Temporary Credential Provisioning")
    void scenario1_staffRegistration() throws Exception {
        String staffEmail = "carlos-" + UUID.randomUUID() + "@easyeat.com";
        StaffRegistrationRequest request = new StaffRegistrationRequest(
                "Carlos Garmendia",
                staffEmail,
                StaffRole.CHEF
        );

        MvcResult result = mockMvc.perform(post("/api/v1/admin/staff")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Carlos Garmendia"))
                .andExpect(jsonPath("$.email").value(staffEmail))
                .andExpect(jsonPath("$.role").value("CHEF"))
                .andExpect(jsonPath("$.status").value("PENDING_FIRST_LOGIN"))
                .andExpect(jsonPath("$.temporaryCredential").isNotEmpty())
                .andReturn();

        StaffRegistrationResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), StaffRegistrationResponse.class);
        assertThat(response.temporaryCredential()).isNotBlank();

        // Verify roster view
        mockMvc.perform(get("/api/v1/admin/staff")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + staffEmail + "')].status").value("PENDING_FIRST_LOGIN"));
    }

    @Test
    @DisplayName("Scenario 2: First Login and Mandatory Password Change")
    void scenario2_firstLoginPasswordChange() throws Exception {
        String staffEmail = "carlos-login-" + UUID.randomUUID() + "@easyeat.com";
        StaffRegistrationRequest regReq = new StaffRegistrationRequest("Carlos FirstLogin", staffEmail, StaffRole.CHEF);

        MvcResult regResult = mockMvc.perform(post("/api/v1/admin/staff")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated())
                .andReturn();

        StaffRegistrationResponse regResp = objectMapper.readValue(regResult.getResponse().getContentAsString(), StaffRegistrationResponse.class);
        String tempPassword = regResp.temporaryCredential();

        // Attempt login before password change -> 403 Account Suspended
        LoginRequest initialLoginReq = new LoginRequest(staffEmail, tempPassword);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialLoginReq)))
                .andExpect(status().isForbidden());

        // Perform first login password change
        Map<String, String> pwdChangeReq = Map.of(
                "email", staffEmail,
                "currentPassword", tempPassword,
                "newPassword", "PermanentChefPass2026!",
                "confirmNewPassword", "PermanentChefPass2026!"
        );

        mockMvc.perform(post("/api/v1/auth/first-login-password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pwdChangeReq)))
                .andExpect(status().isOk());

        // Login with new permanent password
        LoginRequest newLoginReq = new LoginRequest(staffEmail, "PermanentChefPass2026!");
        MvcResult newLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLoginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode tokenNode = objectMapper.readTree(newLoginResult.getResponse().getContentAsString());
        String activeChefToken = tokenNode.has("access_token") ? tokenNode.get("access_token").asText() : tokenNode.get("token").asText();

        // Operational access is restored
        mockMvc.perform(get("/api/v1/chef/ingredients")
                        .header("Authorization", "Bearer " + activeChefToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Scenario 3: RBAC Enforcement and Multi-Tenant Isolation")
    void scenario3_rbacAndMultiTenantIsolation() throws Exception {
        // Create Chef user
        User chef = new User("chef-" + UUID.randomUUID() + "@easyeat.com", passwordEncoder.encode("Pass123!"), UserRole.CHEF);
        chef.setTenantId(tenantId);
        chef.setStatus(UserStatus.ACTIVE);
        chef = userRepository.save(chef);
        String chefToken = jwtService.generateToken(chef);

        // Chef attempts Admin endpoint -> 403 Forbidden
        CreateIngredientRequest ingReq = new CreateIngredientRequest("Rosemary", IngredientCategory.SPICES, UnitOfMeasure.G, new BigDecimal("20.0000"));
        mockMvc.perform(post("/api/v1/admin/ingredients")
                        .header("Authorization", "Bearer " + chefToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ingReq)))
                .andExpect(status().isForbidden());

        // Multi-tenant isolation: ingredient in another tenant
        UUID otherTenant = UUID.randomUUID();
        Ingredient otherIng = new Ingredient(otherTenant, "Other Oil", IngredientCategory.DRY_GOODS, UnitOfMeasure.L, new BigDecimal("10.0000"));
        otherIng = ingredientRepository.save(otherIng);

        // Admin of tenantId queries ingredient of otherTenant -> 404 Not Found
        mockMvc.perform(get("/api/v1/admin/ingredients/{id}", otherIng.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Scenario 4: Ingredient Creation, Validation, and Detail Editing")
    void scenario4_ingredientCreationAndEditing() throws Exception {
        String uniqueName = "Extra Virgin Olive Oil " + UUID.randomUUID();
        CreateIngredientRequest request = new CreateIngredientRequest(
                uniqueName,
                IngredientCategory.DRY_GOODS,
                UnitOfMeasure.L,
                new BigDecimal("4500.0000")
        );

        MvcResult result = mockMvc.perform(post("/api/v1/admin/ingredients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(uniqueName))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStock").value(0))
                .andReturn();

        AdminIngredientDetailResponse created = objectMapper.readValue(result.getResponse().getContentAsString(), AdminIngredientDetailResponse.class);

        // Duplicate name rejection -> 409 Conflict
        mockMvc.perform(post("/api/v1/admin/ingredients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        // Detail editing
        UpdateIngredientRequest updateReq = new UpdateIngredientRequest(
                uniqueName + " Premium",
                IngredientCategory.DRY_GOODS,
                UnitOfMeasure.L,
                new BigDecimal("5000.0000")
        );

        mockMvc.perform(put("/api/v1/admin/ingredients/{id}", created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(uniqueName + " Premium"))
                .andExpect(jsonPath("$.unitPurchaseCost").value(5000.0000));
    }

    @Test
    @DisplayName("Scenario 5: Initial Stock Load and Adjustments with Non-Negative Invariant")
    void scenario5_stockAdjustmentsAndAudit() throws Exception {
        Ingredient ingredient = new Ingredient(tenantId, "Butter " + UUID.randomUUID(), IngredientCategory.DAIRY, UnitOfMeasure.KG, new BigDecimal("2000.0000"));
        ingredient = ingredientRepository.save(ingredient);
        UUID ingredientId = ingredient.getId();

        // 1. Initial Stock Load (Absolute Mode)
        StockAdjustmentRequest absReq = new StockAdjustmentRequest(
                StockAdjustmentMode.ABSOLUTE,
                new BigDecimal("20.0000"),
                "Initial inventory load per receipt #1040"
        );
        mockMvc.perform(post("/api/v1/admin/ingredients/{id}/stock-adjustments", ingredientId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(absReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStock").value(0.0000))
                .andExpect(jsonPath("$.newStock").value(20.0000))
                .andExpect(jsonPath("$.netDelta").value(20.0000));

        // 2. Relative Adjustment (Negative delta)
        StockAdjustmentRequest relReq = new StockAdjustmentRequest(
                StockAdjustmentMode.RELATIVE,
                new BigDecimal("-3.5000"),
                "Broken packaging during restock"
        );
        mockMvc.perform(post("/api/v1/admin/ingredients/{id}/stock-adjustments", ingredientId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(relReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStock").value(20.0000))
                .andExpect(jsonPath("$.newStock").value(16.5000))
                .andExpect(jsonPath("$.netDelta").value(-3.5000));

        // 3. Negative Resulting Stock Rejection (value -25.0000 would cause negative stock)
        StockAdjustmentRequest negReq = new StockAdjustmentRequest(
                StockAdjustmentMode.RELATIVE,
                new BigDecimal("-25.0000"),
                "Excessive deduction"
        );
        mockMvc.perform(post("/api/v1/admin/ingredients/{id}/stock-adjustments", ingredientId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negReq)))
                .andExpect(status().isUnprocessableEntity());

        // 4. Zero Delta Rejection
        StockAdjustmentRequest zeroReq = new StockAdjustmentRequest(
                StockAdjustmentMode.RELATIVE,
                BigDecimal.ZERO,
                "Zero change"
        );
        mockMvc.perform(post("/api/v1/admin/ingredients/{id}/stock-adjustments", ingredientId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroReq)))
                .andExpect(status().isUnprocessableEntity());

        // 5. Immutable Audit Trail Verification
        mockMvc.perform(get("/api/v1/admin/ingredients/{id}/stock-audit", ingredientId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].justification").value("Broken packaging during restock"))
                .andExpect(jsonPath("$[1].justification").value("Initial inventory load per receipt #1040"));
    }

    @Test
    @DisplayName("Scenario 6: Concurrency Validation with Pessimistic Locking")
    void scenario6_concurrencyValidation() throws Exception {
        Ingredient ingredient = new Ingredient(tenantId, "Flour " + UUID.randomUUID(), IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("500.0000"));
        ingredient.setCurrentStock(new BigDecimal("10.0000"));
        ingredient = ingredientRepository.save(ingredient);
        UUID ingredientId = ingredient.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    TenantContextHolder.setTenantId(tenantId);
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(adminUser, null, java.util.List.of())
                    );

                    StockAdjustmentRequest req = new StockAdjustmentRequest(
                            StockAdjustmentMode.RELATIVE,
                            new BigDecimal("-8.0000"),
                            "Concurrent deduction #" + index
                    );
                    stockAdjustmentService.adjustStock(ingredientId, req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Concurrent deduction failed as expected or unexpected: ", e);
                    rejectedCount.incrementAndGet();
                } finally {
                    TenantContextHolder.clear();
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(rejectedCount.get()).isEqualTo(1);

        Ingredient refreshed = ingredientRepository.findByIdAndTenantId(ingredientId, tenantId).orElseThrow();
        assertThat(refreshed.getCurrentStock().compareTo(new BigDecimal("2.0000"))).isZero();
    }

    @Test
    @DisplayName("Scenario 7: Ingredient Lifecycle and Reactivation")
    void scenario7_lifecycleAndReactivation() throws Exception {
        Ingredient ingredient = new Ingredient(tenantId, "Truffle Oil " + UUID.randomUUID(), IngredientCategory.DRY_GOODS, UnitOfMeasure.L, new BigDecimal("12000.0000"));
        ingredient.setCurrentStock(new BigDecimal("16.5000"));
        ingredient = ingredientRepository.save(ingredient);
        UUID ingredientId = ingredient.getId();

        // 1. Discontinue with positive stock
        IngredientStatusUpdateRequest discReq = new IngredientStatusUpdateRequest(IngredientStatus.DISCONTINUED);
        mockMvc.perform(patch("/api/v1/admin/ingredients/{id}/status", ingredientId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(discReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCONTINUED"));

        // 2. Liquidate stock to zero
        StockAdjustmentRequest liquidateReq = new StockAdjustmentRequest(
                StockAdjustmentMode.ABSOLUTE,
                BigDecimal.ZERO,
                "Liquidation of remaining stock"
        );
        mockMvc.perform(post("/api/v1/admin/ingredients/{id}/stock-adjustments", ingredientId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(liquidateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newStock").value(0.0000));

        // 3. Archive permanently when stock is 0
        IngredientStatusUpdateRequest archiveReq = new IngredientStatusUpdateRequest(IngredientStatus.ARCHIVED);
        mockMvc.perform(patch("/api/v1/admin/ingredients/{id}/status", ingredientId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(archiveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("Scenario 8: Kitchen Ingredient Catalog Exploration by Chef")
    void scenario8_chefCatalogExploration() throws Exception {
        // Create Chef
        User chef = new User("chef-catalog-" + UUID.randomUUID() + "@easyeat.com", passwordEncoder.encode("Chef123!"), UserRole.CHEF);
        chef.setTenantId(tenantId);
        chef.setStatus(UserStatus.ACTIVE);
        chef = userRepository.save(chef);
        String chefToken = jwtService.generateToken(chef);

        // Seed 1 active ingredient with stock 0, 1 active ingredient with stock > 0
        Ingredient ing1 = new Ingredient(tenantId, "Apple " + UUID.randomUUID(), IngredientCategory.PRODUCE, UnitOfMeasure.KG, new BigDecimal("120.0000"));
        ing1.setCurrentStock(BigDecimal.ZERO);
        ingredientRepository.save(ing1);

        Ingredient ing2 = new Ingredient(tenantId, "Banana " + UUID.randomUUID(), IngredientCategory.PRODUCE, UnitOfMeasure.KG, new BigDecimal("150.0000"));
        ing2.setCurrentStock(new BigDecimal("10.0000"));
        ingredientRepository.save(ing2);

        // Query catalog
        mockMvc.perform(get("/api/v1/chef/ingredients?page=0&size=20")
                        .header("Authorization", "Bearer " + chefToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                // Ensure financial cost suppression
                .andExpect(jsonPath("$.items[?(@.name == '" + ing1.getName() + "')].stockStatus").value("Out of Stock"))
                .andExpect(jsonPath("$.items[?(@.name == '" + ing2.getName() + "')].stockStatus").value("In Stock"))
                .andExpect(jsonPath("$.items[*].unitPurchaseCost").doesNotExist())
                .andExpect(jsonPath("$.items[*].purchaseCost").doesNotExist())
                .andExpect(jsonPath("$.items[*].auditHistory").doesNotExist());
    }
}
