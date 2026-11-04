package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.DishChefPauseResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DishChefPauseTest {

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
        dish.setName("Pasta Carbonara");
        dish.setPrice(BigDecimal.valueOf(3500.00));
        dish.setIsActive(true);
        dish.setStatus(DishStatus.PUBLISHED);
        dish.setPausedByChef(false);
        dish.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("testToggleAlwaysOperable: toggle funciona con plato sin stock (EC-011, INV-19)")
    void testToggleAlwaysOperable() {
        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
        when(dishRepository.save(any(Dish.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DishChefPauseResponse response = dishService.toggleChefPause(dishId, true, tenantId);

        assertThat(response).isNotNull();
        assertThat(response.isPausedByChef()).isTrue();
        assertThat(dish.getPausedByChef()).isTrue();
        verify(dishRepository).save(dish);
    }

    @Test
    @DisplayName("testToggleOperableWhenAdminDeactivated: toggle funciona con plato desactivado por admin (EC-012, INV-19)")
    void testToggleOperableWhenAdminDeactivated() {
        dish.setIsActive(false); // Desactivado administrativamente
        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
        when(dishRepository.save(any(Dish.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DishChefPauseResponse response = dishService.toggleChefPause(dishId, true, tenantId);

        assertThat(response).isNotNull();
        assertThat(response.isPausedByChef()).isTrue();
        assertThat(dish.getIsActive()).isFalse(); // Se preserva la desactivación de admin
        assertThat(dish.getPausedByChef()).isTrue();
        verify(dishRepository).save(dish);
    }

    @Test
    @DisplayName("testToggleDoesNotMutateStockOrPrice: pausa no altera stock, receta ni precio (INV-11, US3 AC-3.3)")
    void testToggleDoesNotMutateStockOrPrice() {
        BigDecimal originalPrice = dish.getPrice();
        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
        when(dishRepository.save(any(Dish.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DishChefPauseResponse response = dishService.toggleChefPause(dishId, true, tenantId);

        assertThat(response.isPausedByChef()).isTrue();
        assertThat(dish.getPrice()).isEqualByComparingTo(originalPrice);
        verify(dishRepository).save(dish);
    }

    @Test
    @DisplayName("testUnpauseRestoresVisibility: despausar restaura estado pausedByChef=false (INV-9)")
    void testUnpauseRestoresVisibility() {
        dish.setPausedByChef(true);
        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
        when(dishRepository.save(any(Dish.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DishChefPauseResponse response = dishService.toggleChefPause(dishId, false, tenantId);

        assertThat(response.isPausedByChef()).isFalse();
        assertThat(dish.getPausedByChef()).isFalse();
        verify(dishRepository).save(dish);
    }
}
