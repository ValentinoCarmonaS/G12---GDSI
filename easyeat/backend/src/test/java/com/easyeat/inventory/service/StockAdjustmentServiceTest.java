package com.easyeat.inventory.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.inventory.dto.*;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.entity.StockAuditEntry;
import com.easyeat.inventory.exception.InvalidLifecycleTransitionException;
import com.easyeat.inventory.exception.NegativeStockException;
import com.easyeat.inventory.exception.ZeroDeltaAdjustmentException;
import com.easyeat.inventory.entity.IngredientBatch;
import com.easyeat.inventory.repository.IngredientBatchRepository;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.inventory.repository.StockAuditEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockAdjustmentServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private StockAuditEntryRepository stockAuditEntryRepository;

    @Mock
    private IngredientBatchRepository ingredientBatchRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StockAdjustmentService stockAdjustmentService;

    private UUID tenantId;
    private UUID userId;
    private User adminUser;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);

        adminUser = new User();
        adminUser.setId(userId);
        adminUser.setEmail("admin@easyeat.com");
        adminUser.setFullName("Admin Master");

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(adminUser, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("adjustStock - Absolute mode updates stock and persists audit entry")
    void adjustStock_absoluteMode_success() {
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Tomato", IngredientCategory.PRODUCE, UnitOfMeasure.KG, new BigDecimal("100.0000"));
        ingredient.setId(ingredientId);
        ingredient.setCurrentStock(new BigDecimal("10.0000"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentMode.ABSOLUTE,
                new BigDecimal("14.0000"),
                "Physical count reconciliation"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));
        when(stockAuditEntryRepository.save(any(StockAuditEntry.class))).thenAnswer(inv -> {
            StockAuditEntry entry = inv.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        StockAdjustmentResponse response = stockAdjustmentService.adjustStock(ingredientId, request);

        assertThat(response).isNotNull();
        assertThat(response.previousStock()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(response.newStock()).isEqualByComparingTo(new BigDecimal("14.0000"));
        assertThat(response.netDelta()).isEqualByComparingTo(new BigDecimal("4.0000"));
        assertThat(ingredient.getCurrentStock()).isEqualByComparingTo(new BigDecimal("14.0000"));

        ArgumentCaptor<StockAuditEntry> auditCaptor = ArgumentCaptor.forClass(StockAuditEntry.class);
        verify(stockAuditEntryRepository).save(auditCaptor.capture());
        StockAuditEntry captured = auditCaptor.getValue();
        assertThat(captured.getAdjustmentMode()).isEqualTo(StockAdjustmentMode.ABSOLUTE);
        assertThat(captured.getPreviousStock()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(captured.getNewStock()).isEqualByComparingTo(new BigDecimal("14.0000"));
        assertThat(captured.getNetDelta()).isEqualByComparingTo(new BigDecimal("4.0000"));
        assertThat(captured.getJustification()).isEqualTo("Physical count reconciliation");
    }

    @Test
    @DisplayName("adjustStock - Relative mode positive delta increments stock")
    void adjustStock_relativeModePositive_success() {
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Flour", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("50.0000"));
        ingredient.setId(ingredientId);
        ingredient.setCurrentStock(new BigDecimal("10.0000"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentMode.RELATIVE,
                new BigDecimal("25.0000"),
                "Delivery receipt invoice #8812"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));
        when(stockAuditEntryRepository.save(any(StockAuditEntry.class))).thenAnswer(inv -> {
            StockAuditEntry entry = inv.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        StockAdjustmentResponse response = stockAdjustmentService.adjustStock(ingredientId, request);

        assertThat(response.previousStock()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(response.newStock()).isEqualByComparingTo(new BigDecimal("35.0000"));
        assertThat(response.netDelta()).isEqualByComparingTo(new BigDecimal("25.0000"));
    }

    @Test
    @DisplayName("adjustStock - Relative mode negative delta decrements stock")
    void adjustStock_relativeModeNegative_success() {
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Flour", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("50.0000"));
        ingredient.setId(ingredientId);
        ingredient.setCurrentStock(new BigDecimal("10.0000"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentMode.RELATIVE,
                new BigDecimal("-3.0000"),
                "Used for preparation"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));
        when(stockAuditEntryRepository.save(any(StockAuditEntry.class))).thenAnswer(inv -> {
            StockAuditEntry entry = inv.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        StockAdjustmentResponse response = stockAdjustmentService.adjustStock(ingredientId, request);

        assertThat(response.previousStock()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(response.newStock()).isEqualByComparingTo(new BigDecimal("7.0000"));
        assertThat(response.netDelta()).isEqualByComparingTo(new BigDecimal("-3.0000"));
    }

    @Test
    @DisplayName("adjustStock - resulting stock below zero throws NegativeStockException")
    void adjustStock_negativeResultingStock_throwsNegativeStockException() {
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Sugar", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("40.0000"));
        ingredient.setId(ingredientId);
        ingredient.setCurrentStock(new BigDecimal("5.0000"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentMode.RELATIVE,
                new BigDecimal("-8.0000"),
                "Excess spoilage write-off"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));

        assertThatThrownBy(() -> stockAdjustmentService.adjustStock(ingredientId, request))
                .isInstanceOf(NegativeStockException.class);

        verify(stockAuditEntryRepository, never()).save(any());
        verify(ingredientRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustStock - relative adjustment with zero delta throws ZeroDeltaAdjustmentException")
    void adjustStock_zeroDeltaRelative_throwsZeroDeltaAdjustmentException() {
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Olive Oil", IngredientCategory.DRY_GOODS, UnitOfMeasure.L, new BigDecimal("1000.0000"));
        ingredient.setId(ingredientId);
        ingredient.setCurrentStock(new BigDecimal("2.0000"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentMode.RELATIVE,
                BigDecimal.ZERO,
                "Non-operational delta"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));

        assertThatThrownBy(() -> stockAdjustmentService.adjustStock(ingredientId, request))
                .isInstanceOf(ZeroDeltaAdjustmentException.class);

        verify(stockAuditEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustStock - absolute adjustment identical to current stock throws ZeroDeltaAdjustmentException")
    void adjustStock_zeroDeltaAbsolute_throwsZeroDeltaAdjustmentException() {
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Salt", IngredientCategory.SPICES, UnitOfMeasure.KG, new BigDecimal("20.0000"));
        ingredient.setId(ingredientId);
        ingredient.setCurrentStock(new BigDecimal("10.0000"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentMode.ABSOLUTE,
                new BigDecimal("10.0000"),
                "Same physical count"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));

        assertThatThrownBy(() -> stockAdjustmentService.adjustStock(ingredientId, request))
                .isInstanceOf(ZeroDeltaAdjustmentException.class);

        verify(stockAuditEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustStock - discrete unit 'u' with fractional value throws IllegalArgumentException")
    void adjustStock_discreteUnitFractional_throwsIllegalArgumentException() {
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Eggs", IngredientCategory.DAIRY, UnitOfMeasure.U, new BigDecimal("50.0000"));
        ingredient.setId(ingredientId);
        ingredient.setCurrentStock(new BigDecimal("12.0000"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentMode.ABSOLUTE,
                new BigDecimal("12.5000"),
                "Half egg counted"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));

        assertThatThrownBy(() -> stockAdjustmentService.adjustStock(ingredientId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Discrete unit 'u'");

        verify(stockAuditEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustStock - adjustment on DISCONTINUED ingredient is allowed until stock reaches zero")
    void adjustStock_discontinuedAllowed() {
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Truffle", IngredientCategory.SPICES, UnitOfMeasure.G, new BigDecimal("10000.0000"));
        ingredient.setId(ingredientId);
        ingredient.setStatus(IngredientStatus.DISCONTINUED);
        ingredient.setCurrentStock(new BigDecimal("3.0000"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentMode.RELATIVE,
                new BigDecimal("-3.0000"),
                "Spoilage write-off of discontinued stock"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));
        when(stockAuditEntryRepository.save(any(StockAuditEntry.class))).thenAnswer(inv -> {
            StockAuditEntry entry = inv.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        StockAdjustmentResponse response = stockAdjustmentService.adjustStock(ingredientId, request);

        assertThat(response.newStock()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ingredient.getCurrentStock()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("adjustStock - ADD action creates IngredientBatch and increments global stock")
    void adjustStock_addAction_createsBatchAndIncreasesStock() {
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Apple", IngredientCategory.PRODUCE, UnitOfMeasure.KG, new BigDecimal("2000.0000"));
        ingredient.setId(ingredientId);
        ingredient.setCurrentStock(BigDecimal.ZERO);

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentAction.ADD,
                null,
                new BigDecimal("5.0000"),
                null,
                new BigDecimal("2000.0000"),
                null,
                "Purchase of Batch A"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));
        when(ingredientBatchRepository.save(any(IngredientBatch.class))).thenAnswer(inv -> {
            IngredientBatch b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
        when(stockAuditEntryRepository.save(any(StockAuditEntry.class))).thenAnswer(inv -> {
            StockAuditEntry e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        StockAdjustmentResponse response = stockAdjustmentService.adjustStock(ingredientId, request);

        assertThat(response.newStock()).isEqualByComparingTo(new BigDecimal("5.0000"));
        assertThat(response.netDelta()).isEqualByComparingTo(new BigDecimal("5.0000"));
        assertThat(ingredient.getCurrentStock()).isEqualByComparingTo(new BigDecimal("5.0000"));

        ArgumentCaptor<IngredientBatch> batchCaptor = ArgumentCaptor.forClass(IngredientBatch.class);
        verify(ingredientBatchRepository).save(batchCaptor.capture());
        IngredientBatch savedBatch = batchCaptor.getValue();
        assertThat(savedBatch.getInitialQuantity()).isEqualByComparingTo(new BigDecimal("5.0000"));
        assertThat(savedBatch.getRemainingQuantity()).isEqualByComparingTo(new BigDecimal("5.0000"));
        assertThat(savedBatch.getUnitCost()).isEqualByComparingTo(new BigDecimal("2000.0000"));
    }

    @Test
    @DisplayName("adjustStock - ADD action missing unitCost throws IllegalArgumentException")
    void adjustStock_addAction_missingUnitCostThrows() {
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Apple", IngredientCategory.PRODUCE, UnitOfMeasure.KG, new BigDecimal("2000.0000"));
        ingredient.setId(ingredientId);

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentAction.ADD,
                null,
                new BigDecimal("5.0000"),
                null,
                null,
                null,
                "Purchase without cost"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));

        assertThatThrownBy(() -> stockAdjustmentService.adjustStock(ingredientId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unit purchase cost is required");
    }

    @Test
    @DisplayName("adjustStock - SUBTRACT action deducts from batch and calculates exact financial loss")
    void adjustStock_subtractAction_deductsFromBatchAndCalculatesLoss() {
        UUID ingredientId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Apple", IngredientCategory.PRODUCE, UnitOfMeasure.KG, new BigDecimal("2000.0000"));
        ingredient.setId(ingredientId);
        ingredient.setCurrentStock(new BigDecimal("10.0000"));

        IngredientBatch batchA = new IngredientBatch(tenantId, ingredientId, new BigDecimal("5.0000"), new BigDecimal("2000.0000"));
        batchA.setId(batchId);
        batchA.setRemainingQuantity(new BigDecimal("5.0000"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentAction.SUBTRACT,
                null,
                new BigDecimal("2.0000"),
                null,
                null,
                batchId,
                "Shrinkage of 2 kg"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));
        when(ingredientBatchRepository.findByIdAndTenantIdForUpdate(batchId, tenantId)).thenReturn(Optional.of(batchA));
        when(stockAuditEntryRepository.save(any(StockAuditEntry.class))).thenAnswer(inv -> {
            StockAuditEntry e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        StockAdjustmentResponse response = stockAdjustmentService.adjustStock(ingredientId, request);

        assertThat(response.newStock()).isEqualByComparingTo(new BigDecimal("8.0000"));
        assertThat(response.netDelta()).isEqualByComparingTo(new BigDecimal("-2.0000"));
        assertThat(batchA.getRemainingQuantity()).isEqualByComparingTo(new BigDecimal("3.0000"));

        ArgumentCaptor<StockAuditEntry> auditCaptor = ArgumentCaptor.forClass(StockAuditEntry.class);
        verify(stockAuditEntryRepository).save(auditCaptor.capture());
        StockAuditEntry entry = auditCaptor.getValue();
        assertThat(entry.getBatchId()).isEqualTo(batchId);
        assertThat(entry.getUnitCost()).isEqualByComparingTo(new BigDecimal("2000.0000"));
        assertThat(entry.getFinancialLoss()).isEqualByComparingTo(new BigDecimal("4000.0000"));
    }

    @Test
    @DisplayName("adjustStock - SUBTRACT action with quantity exceeding batch balance throws NegativeStockException")
    void adjustStock_subtractAction_exceedingBatchBalanceThrows() {
        UUID ingredientId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Apple", IngredientCategory.PRODUCE, UnitOfMeasure.KG, new BigDecimal("2000.0000"));
        ingredient.setId(ingredientId);
        ingredient.setCurrentStock(new BigDecimal("10.0000"));

        IngredientBatch batchA = new IngredientBatch(tenantId, ingredientId, new BigDecimal("5.0000"), new BigDecimal("2000.0000"));
        batchA.setId(batchId);
        batchA.setRemainingQuantity(new BigDecimal("3.0000"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentAction.SUBTRACT,
                null,
                new BigDecimal("4.0000"),
                null,
                null,
                batchId,
                "Shrinkage of 4 kg"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));
        when(ingredientBatchRepository.findByIdAndTenantIdForUpdate(batchId, tenantId)).thenReturn(Optional.of(batchA));

        assertThatThrownBy(() -> stockAdjustmentService.adjustStock(ingredientId, request))
                .isInstanceOf(NegativeStockException.class)
                .hasMessageContaining("exceeds available batch balance");
    }

    @Test
    @DisplayName("adjustStock - SUBTRACT action missing batchId throws IllegalArgumentException")
    void adjustStock_subtractAction_missingBatchIdThrows() {
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Apple", IngredientCategory.PRODUCE, UnitOfMeasure.KG, new BigDecimal("2000.0000"));
        ingredient.setId(ingredientId);
        ingredient.setCurrentStock(new BigDecimal("10.0000"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentAction.SUBTRACT,
                null,
                new BigDecimal("2.0000"),
                null,
                null,
                null,
                "Shrinkage without batch"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));

        assertThatThrownBy(() -> stockAdjustmentService.adjustStock(ingredientId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch ID is required");
    }

    @Test
    @DisplayName("adjustStock - ADD action on DISCONTINUED ingredient throws InvalidLifecycleTransitionException")
    void adjustStock_discontinuedAdditionBlocked() {
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Old Salmon", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("5000.0000"));
        ingredient.setId(ingredientId);
        ingredient.setStatus(IngredientStatus.DISCONTINUED);
        ingredient.setCurrentStock(BigDecimal.ZERO);

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentAction.ADD,
                null,
                new BigDecimal("5.0000"),
                null,
                new BigDecimal("5000.0000"),
                null,
                "Attempt to add stock to discontinued item"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));

        assertThatThrownBy(() -> stockAdjustmentService.adjustStock(ingredientId, request))
                .isInstanceOf(InvalidLifecycleTransitionException.class)
                .hasMessageContaining("DISCONTINUED");

        verify(stockAuditEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustStock - SUBTRACT action on DISCONTINUED ingredient is permitted")
    void adjustStock_discontinuedSubtractionAllowed() {
        UUID ingredientId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Old Salmon", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("5000.0000"));
        ingredient.setId(ingredientId);
        ingredient.setStatus(IngredientStatus.DISCONTINUED);
        ingredient.setCurrentStock(new BigDecimal("5.0000"));

        IngredientBatch batch = new IngredientBatch(tenantId, ingredientId, new BigDecimal("5.0000"), new BigDecimal("5000.0000"));
        batch.setId(batchId);
        batch.setRemainingQuantity(new BigDecimal("5.0000"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentAction.SUBTRACT,
                null,
                new BigDecimal("2.0000"),
                null,
                null,
                batchId,
                "Write-off of discontinued stock"
        );

        when(ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));
        when(ingredientBatchRepository.findByIdAndTenantIdForUpdate(batchId, tenantId)).thenReturn(Optional.of(batch));
        when(stockAuditEntryRepository.save(any(StockAuditEntry.class))).thenAnswer(inv -> {
            StockAuditEntry e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        StockAdjustmentResponse response = stockAdjustmentService.adjustStock(ingredientId, request);

        assertThat(response.newStock()).isEqualByComparingTo(new BigDecimal("3.0000"));
        assertThat(ingredient.getCurrentStock()).isEqualByComparingTo(new BigDecimal("3.0000"));
    }

    @Test
    @DisplayName("getAuditHistory - returns descending list of audit records with adminName")
    void getAuditHistory_success() {
        UUID ingredientId = UUID.randomUUID();
        StockAuditEntry entry = new StockAuditEntry(
                tenantId,
                ingredientId,
                userId,
                StockAdjustmentMode.ABSOLUTE,
                new BigDecimal("10.0000"),
                new BigDecimal("14.0000"),
                new BigDecimal("4.0000"),
                "Audit note"
        );
        entry.setId(UUID.randomUUID());
        entry.setCreatedAt(Instant.now());

        when(stockAuditEntryRepository.findAllByTenantIdAndIngredientIdOrderByCreatedAtDesc(tenantId, ingredientId))
                .thenReturn(List.of(entry));
        when(userRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        List<StockAuditEntryResponse> history = stockAdjustmentService.getAuditHistory(ingredientId);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).adminName()).isEqualTo("Admin Master");
        assertThat(history.get(0).justification()).isEqualTo("Audit note");
    }
}
