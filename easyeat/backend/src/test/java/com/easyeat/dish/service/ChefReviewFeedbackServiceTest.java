package com.easyeat.dish.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.dish.domain.DishReviewHistory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.ChefReviewFeedbackEntry;
import com.easyeat.dish.dto.ChefReviewFeedbackResponse;
import com.easyeat.dish.repository.DishReviewHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FR-023, SC-003: the Chef's financially-blind read of their own establishment's rejection
 * feedback, newest cycle first.
 */
@DisplayName("ChefReviewFeedbackService")
class ChefReviewFeedbackServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    private DishAccessGuard accessGuard;
    private DishReviewHistoryRepository reviewHistoryRepository;
    private UserRepository userRepository;
    private ChefReviewFeedbackService service;

    private Dish dish;

    @BeforeEach
    void setUp() {
        accessGuard = Mockito.mock(DishAccessGuard.class);
        reviewHistoryRepository = Mockito.mock(DishReviewHistoryRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        service = new ChefReviewFeedbackService(accessGuard, reviewHistoryRepository, userRepository);

        TenantContextHolder.setTenantId(TENANT);

        dish = new Dish();
        dish.setId(UUID.randomUUID());
        dish.setTenantId(TENANT);
        dish.setStatus(DishStatus.DRAFT);

        when(accessGuard.requireOwnedByTenant(dish.getId(), TENANT)).thenReturn(dish);
        when(reviewHistoryRepository.findByDishIdAndTenantIdOrderByCreatedAtAsc(dish.getId(), TENANT))
                .thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("only REJECTED events become feedback; APPROVED events are excluded")
    void onlyRejectedEventsAreFeedback() {
        UUID adminId = UUID.randomUUID();
        User admin = new User();
        admin.setId(adminId);
        admin.setFullName("Lucía Ferraro");
        when(userRepository.findAllById(any())).thenReturn(List.of(admin));

        DishReviewHistory approved = DishReviewHistory.approved(dish.getId(), TENANT, 1, adminId);
        DishReviewHistory rejected = DishReviewHistory.rejected(dish.getId(), TENANT, 2, adminId,
                "Faltan las fotos del plato, por favor adjuntalas.", null);
        when(reviewHistoryRepository.findByDishIdAndTenantIdOrderByCreatedAtAsc(dish.getId(), TENANT))
                .thenReturn(List.of(approved, rejected));

        ChefReviewFeedbackResponse response = service.getFeedback(dish.getId());

        assertThat(response.entries()).hasSize(1);
        assertThat(response.entries().get(0).cycleNumber()).isEqualTo(2);
        assertThat(response.entries().get(0).reviewerName()).isEqualTo("Lucía Ferraro");
        assertThat(response.entries().get(0).comment())
                .isEqualTo("Faltan las fotos del plato, por favor adjuntalas.");
    }

    @Test
    @DisplayName("entries are ordered newest cycle first")
    void entriesOrderedNewestFirst() throws InterruptedException {
        UUID adminId = UUID.randomUUID();
        DishReviewHistory first = DishReviewHistory.rejected(dish.getId(), TENANT, 1, adminId,
                "Primera observación: faltan las fotos.", null);
        Thread.sleep(5);
        DishReviewHistory second = DishReviewHistory.rejected(dish.getId(), TENANT, 2, adminId,
                "Segunda observación: revisá el costo del salmón.", null);
        when(reviewHistoryRepository.findByDishIdAndTenantIdOrderByCreatedAtAsc(dish.getId(), TENANT))
                .thenReturn(List.of(first, second));

        ChefReviewFeedbackResponse response = service.getFeedback(dish.getId());

        assertThat(response.entries()).hasSize(2);
        assertThat(response.entries().get(0).cycleNumber()).isEqualTo(2);
        assertThat(response.entries().get(1).cycleNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("a dish never rejected returns an empty list, not an error")
    void neverRejectedReturnsEmptyList() {
        ChefReviewFeedbackResponse response = service.getFeedback(dish.getId());

        assertThat(response.entries()).isEmpty();
        assertThat(response.dishId()).isEqualTo(dish.getId());
        assertThat(response.status()).isEqualTo(DishStatus.DRAFT);
    }

    @Test
    @DisplayName("a reviewer whose account no longer resolves falls back to a generic label")
    void unresolvedReviewerFallsBack() {
        UUID adminId = UUID.randomUUID();
        DishReviewHistory rejected = DishReviewHistory.rejected(dish.getId(), TENANT, 1, adminId,
                "Faltan las fotos del plato, por favor adjuntalas.", null);
        when(reviewHistoryRepository.findByDishIdAndTenantIdOrderByCreatedAtAsc(dish.getId(), TENANT))
                .thenReturn(List.of(rejected));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        ChefReviewFeedbackResponse response = service.getFeedback(dish.getId());

        assertThat(response.entries().get(0).reviewerName()).isEqualTo("Administrador");
    }

    @Test
    @DisplayName("a dish that does not exist anywhere is 404")
    void unknownDish() {
        when(accessGuard.requireOwnedByTenant(dish.getId(), TENANT))
                .thenThrow(new DishNotFoundException(dish.getId()));

        assertThatThrownBy(() -> service.getFeedback(dish.getId()))
                .isInstanceOf(DishNotFoundException.class);
    }

    @Test
    @DisplayName("a dish belonging to another establishment is forbidden, not merely absent")
    void crossTenantDishIsForbidden() {
        when(accessGuard.requireOwnedByTenant(dish.getId(), TENANT))
                .thenThrow(new TenantViolationException(TENANT, UUID.randomUUID()));

        assertThatThrownBy(() -> service.getFeedback(dish.getId()))
                .isInstanceOf(TenantViolationException.class);
    }

    @Test
    @DisplayName("a rejection that carried a proposal surfaces the snapshot to the Chef (FR-042)")
    void rejectionSurfacesProposedSnapshot() {
        UUID adminId = UUID.randomUUID();
        DishReviewHistory rejected = DishReviewHistory.rejected(dish.getId(), TENANT, 2, adminId,
                "El costo del salmón dispara el food cost.", "{\"title\":\"Milanesa de ternera\","
                        + "\"description\":\"Cambia la carne por ternera.\",\"allergenCodes\":[\"GLUTEN\"],"
                        + "\"dietaryLifestyleCodes\":null,\"ingredients\":[{\"ingredientId\":\""
                        + UUID.randomUUID() + "\",\"quantity\":200}]}");
        when(reviewHistoryRepository.findByDishIdAndTenantIdOrderByCreatedAtAsc(dish.getId(), TENANT))
                .thenReturn(List.of(rejected));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        ChefReviewFeedbackResponse response = service.getFeedback(dish.getId());

        assertThat(response.entries()).hasSize(1);
        assertThat(response.entries().get(0).cycleNumber()).isEqualTo(2);
        assertThat(response.entries().get(0).proposedChanges()).isNotNull();
        assertThat(response.entries().get(0).proposedChanges().title())
                .isEqualTo("Milanesa de ternera");
        assertThat(response.entries().get(0).proposedChanges().ingredients()).hasSize(1);
    }

    @Test
    @DisplayName("the Chef feedback entry declares no financial field at all, by construction "
            + "(FR-023, SC-003)")
    void entryDeclaresNoFinancialField() {
        List<String> names = java.util.Arrays.stream(ChefReviewFeedbackEntry.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(names)
                .as("no cost, Food Cost, margin or price may exist on a Chef-facing entry")
                .doesNotContainAnyElementsOf(List.of("cost", "foodCost", "food_cost", "margin", "price",
                        "retailPrice", "unitPurchaseCost"));
    }
}
