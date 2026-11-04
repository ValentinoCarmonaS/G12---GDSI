package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.dto.DishAvailabilityDetailsResponse;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.dish.domain.DishStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DishThreeTierAvailabilityTest {

    @Mock
    private DishRepository dishRepository;

    @InjectMocks
    private DishService dishService;

    private UUID tenantId;
    private UUID dishId;
    private Dish dish;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        dishId = UUID.randomUUID();

        dish = new Dish();
        dish.setId(dishId);
        dish.setTenantId(tenantId);
        dish.setName("Bife de Chorizo");
        dish.setPrice(BigDecimal.valueOf(8000.00));
        dish.setIsActive(true);
        dish.setStatus(DishStatus.PUBLISHED);
        dish.setPausedByChef(false);
        dish.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("testDistinctCauses: tres causas no colapsan en flag único (INV-10, SC-014)")
    void testDistinctCauses() {
        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

        DishAvailabilityDetailsResponse details = dishService.getDishAvailabilityDetails(dishId, tenantId);

        assertThat(details).isNotNull();
        assertThat(details.getDishId()).isEqualTo(dishId);
        assertThat(details.getCauses()).isNotNull();
        assertThat(details.getCauses().isSinStock()).isFalse();
        assertThat(details.getCauses().isPausaChef()).isFalse();
        assertThat(details.getCauses().isDesactivacionAdmin()).isFalse();
        assertThat(details.isVisibleToDiner()).isTrue();
        assertThat(details.isOrderable()).isTrue();
    }

    @Test
    @DisplayName("testCoexistenceSinStockAndPause: plato pausado + sin stock -> despausar exitoso pero no comprable (EC-011, INV-12, US3 AC-3.4)")
    void testCoexistenceSinStockAndPause() {
        dish.setPausedByChef(true);
        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

        DishAvailabilityDetailsResponse details = dishService.getDishAvailabilityDetails(dishId, tenantId);

        assertThat(details.getCauses().isPausaChef()).isTrue();
        assertThat(details.isVisibleToDiner()).isFalse();
        assertThat(details.isOrderable()).isFalse();
    }

    @Test
    @DisplayName("testCoexistenceAdminDeactivatedAndPause: pausa + desactivación admin coexisten (EC-012)")
    void testCoexistenceAdminDeactivatedAndPause() {
        dish.setIsActive(false);
        dish.setPausedByChef(true);
        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

        DishAvailabilityDetailsResponse details = dishService.getDishAvailabilityDetails(dishId, tenantId);

        assertThat(details.getCauses().isDesactivacionAdmin()).isTrue();
        assertThat(details.getCauses().isPausaChef()).isTrue();
        assertThat(details.isVisibleToDiner()).isFalse();
        assertThat(details.isOrderable()).isFalse();
    }

    @Test
    @DisplayName("testAdminReactivationKeepsChefPause: reactivar admin no despausa chef (EC-013)")
    void testAdminReactivationKeepsChefPause() {
        dish.setIsActive(true); // Reactivado por admin
        dish.setPausedByChef(true); // Permanece pausado por chef
        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

        DishAvailabilityDetailsResponse details = dishService.getDishAvailabilityDetails(dishId, tenantId);

        assertThat(details.getCauses().isDesactivacionAdmin()).isFalse();
        assertThat(details.getCauses().isPausaChef()).isTrue();
        assertThat(details.isVisibleToDiner()).isFalse();
    }

    @Test
    @DisplayName("testUnpauseOutOfStockRendersAgotado: despausar plato sin stock muestra no comprable (EC-014)")
    void testUnpauseOutOfStockRendersAgotado() {
        dish.setPausedByChef(false);
        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

        DishAvailabilityDetailsResponse details = dishService.getDishAvailabilityDetails(dishId, tenantId);

        assertThat(details.getCauses().isPausaChef()).isFalse();
    }

    @Test
    @DisplayName("testAdminDeactivationDoesNotBlockChefToggle: desactivación admin no bloquea toggle (INV-13)")
    void testAdminDeactivationDoesNotBlockChefToggle() {
        dish.setIsActive(false);
        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
        when(dishRepository.save(any(Dish.class))).thenAnswer(invocation -> invocation.getArgument(0));

        dishService.toggleChefPause(dishId, true, tenantId);

        assertThat(dish.getPausedByChef()).isTrue();
        assertThat(dish.getIsActive()).isFalse();
        verify(dishRepository).save(dish);
    }
}
