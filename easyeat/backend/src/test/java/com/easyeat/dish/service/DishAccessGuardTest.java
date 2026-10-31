package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.exception.TenantViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * FR-001, SC-003: 404 only when no dish with this id exists at all; a dish that exists but
 * belongs to another tenant is 403, never 404 — spec.md's Edge Cases rule out 404 as an
 * alternative here.
 *
 * <p>Exercised directly against the real implementation, not only through the mock every calling
 * service's own test uses for it: a mocked collaborator proves nothing about the collaborator's
 * own logic, only about how its caller reacts to a given return value.
 */
@DisplayName("DishAccessGuard")
class DishAccessGuardTest {

    private static final UUID TENANT = UUID.randomUUID();

    private DishRepository dishRepository;
    private DishAccessGuard guard;

    @BeforeEach
    void setUp() {
        dishRepository = Mockito.mock(DishRepository.class);
        guard = new DishAccessGuard(dishRepository);
    }

    @Test
    @DisplayName("returns the dish when it belongs to the caller's tenant")
    void returnsTheDishWhenOwned() {
        Dish dish = new Dish();
        dish.setId(UUID.randomUUID());
        dish.setTenantId(TENANT);
        when(dishRepository.findById(dish.getId())).thenReturn(Optional.of(dish));

        assertThat(guard.requireOwnedByTenant(dish.getId(), TENANT)).isSameAs(dish);
    }

    @Test
    @DisplayName("a dish that does not exist anywhere is 404")
    void notFoundWhenAbsentEntirely() {
        UUID id = UUID.randomUUID();
        when(dishRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireOwnedByTenant(id, TENANT))
                .isInstanceOf(DishNotFoundException.class);
    }

    @Test
    @DisplayName("a dish that exists under another tenant is 403, never 404")
    void forbiddenWhenAnotherTenantsDish() {
        Dish dish = new Dish();
        dish.setId(UUID.randomUUID());
        dish.setTenantId(UUID.randomUUID());
        when(dishRepository.findById(dish.getId())).thenReturn(Optional.of(dish));

        assertThatThrownBy(() -> guard.requireOwnedByTenant(dish.getId(), TENANT))
                .isInstanceOf(TenantViolationException.class)
                .isNotInstanceOf(DishNotFoundException.class);
    }
}
