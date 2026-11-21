package com.easyeat.analytics.service;

import com.easyeat.analytics.dto.DishRankingItemResponse;
import com.easyeat.analytics.repository.AnalyticsRepository;
import com.easyeat.analytics.repository.AnalyticsRepository.DishRankingProjection;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.common.exception.TenantViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DishRankingServiceTest {

    @Mock
    private AnalyticsRepository analyticsRepository;

    @Mock
    private EstablishmentRepository establishmentRepository;

    private DishRankingService service;

    private final UUID tenantId = UUID.randomUUID();
    // 2026-11-20 is a Friday. Time in UTC-3: 2026-11-20T15:00:00-03:00 => 2026-11-20T18:00:00Z
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-20T18:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);
        service = new DishRankingService(analyticsRepository, establishmentRepository, fixedClock);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private DishRankingProjection projection(UUID dishId, String dishName, long unitsSold) {
        return new DishRankingProjection() {
            @Override
            public UUID getDishId() {
                return dishId;
            }

            @Override
            public String getDishName() {
                return dishName;
            }

            @Override
            public Long getUnitsSold() {
                return unitsSold;
            }
        };
    }

    private void mockEstablishmentTimezone(String timezone) {
        Establishment est = new Establishment();
        est.setId(tenantId);
        est.setTimezone(timezone);
        when(establishmentRepository.findById(tenantId)).thenReturn(Optional.of(est));
    }

    @Test
    void getDishRanking_allGranularity_passesNullBoundsAndMapsSortedProjections() {
        UUID d1 = UUID.randomUUID();
        UUID d2 = UUID.randomUUID();
        UUID d3 = UUID.randomUUID();

        when(analyticsRepository.findDishRankingAll(
                eq(tenantId),
                eq(AnalyticsRepository.COMPLETED_ORDER_STATUSES)
        )).thenReturn(List.of(
                projection(d1, "Empanada de Carne Suave", 85L),
                projection(d2, "Hamburguesa Clasica", 42L),
                projection(d3, "Pizza Muzzarella", 42L)
        ));

        List<DishRankingItemResponse> result = service.getDishRanking("all");

        assertThat(result).containsExactly(
                new DishRankingItemResponse(d1, "Empanada de Carne Suave", 85L),
                new DishRankingItemResponse(d2, "Hamburguesa Clasica", 42L),
                new DishRankingItemResponse(d3, "Pizza Muzzarella", 42L)
        );
    }

    @Test
    void getDishRanking_dayGranularity_usesEstablishmentTimezoneBounds() {
        mockEstablishmentTimezone("America/Argentina/Buenos_Aires");
        when(analyticsRepository.findDishRanking(any(), any(), any(), any())).thenReturn(List.of());

        List<DishRankingItemResponse> result = service.getDishRanking("day");

        assertThat(result).isEmpty();

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(analyticsRepository).findDishRanking(
                eq(tenantId),
                eq(AnalyticsRepository.COMPLETED_ORDER_STATUSES),
                startCaptor.capture(),
                endCaptor.capture()
        );

        assertThat(startCaptor.getValue()).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));
        assertThat(endCaptor.getValue()).isEqualTo(Instant.parse("2026-11-21T03:00:00Z"));
    }

    @Test
    void getDishRanking_weekAndMonthGranularities_computeIsoBounds() {
        mockEstablishmentTimezone("America/Argentina/Buenos_Aires");
        when(analyticsRepository.findDishRanking(any(), any(), any(), any())).thenReturn(List.of());

        service.getDishRanking("week");
        ArgumentCaptor<Instant> weekStart = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> weekEnd = ArgumentCaptor.forClass(Instant.class);
        verify(analyticsRepository).findDishRanking(
                eq(tenantId),
                eq(AnalyticsRepository.COMPLETED_ORDER_STATUSES),
                weekStart.capture(),
                weekEnd.capture()
        );
        assertThat(weekStart.getValue()).isEqualTo(Instant.parse("2026-11-16T03:00:00Z"));
        assertThat(weekEnd.getValue()).isEqualTo(Instant.parse("2026-11-23T03:00:00Z"));
    }

    @Test
    void getDishRanking_invalidGranularity_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.getDishRanking("quarter"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid granularity 'quarter'. Supported values are: all, day, week, month");
    }

    @Test
    void getDishRanking_missingTenantContext_throwsTenantViolationException() {
        TenantContextHolder.clear();
        assertThatThrownBy(() -> service.getDishRanking("all"))
                .isInstanceOf(TenantViolationException.class);
    }
}
