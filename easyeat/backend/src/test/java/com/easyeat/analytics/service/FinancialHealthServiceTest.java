package com.easyeat.analytics.service;

import com.easyeat.analytics.dto.FinancialHealthResponse;
import com.easyeat.analytics.repository.AnalyticsRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.common.context.TenantContext;
import com.easyeat.common.exception.TenantViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialHealthServiceTest {

    @Mock
    private AnalyticsRepository analyticsRepository;

    @Mock
    private EstablishmentRepository establishmentRepository;

    private FinancialHealthService service;

    private final UUID tenantId = UUID.randomUUID();
    // 2026-11-20 is a Friday. Time in UTC-3: 2026-11-20T15:00:00-03:00 => 2026-11-20T18:00:00Z
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-20T18:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
        service = new FinancialHealthService(analyticsRepository, establishmentRepository, fixedClock);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void mockEstablishmentTimezone(String timezone) {
        Establishment est = new Establishment();
        est.setId(tenantId);
        est.setTimezone(timezone);
        when(establishmentRepository.findById(tenantId)).thenReturn(Optional.of(est));
    }

    private AnalyticsRepository.FinancialHealthProjection projection(BigDecimal revenue, long count) {
        return new AnalyticsRepository.FinancialHealthProjection() {
            @Override
            public BigDecimal getRevenue() {
                return revenue;
            }

            @Override
            public Long getOrderCount() {
                return count;
            }
        };
    }

    @Test
    void getFinancialHealth_dayGranularity_calculatesLocalDayBoundsAndReturnsMetrics() {
        mockEstablishmentTimezone("America/Argentina/Buenos_Aires");
        when(analyticsRepository.aggregateFinancialHealth(eq(tenantId), eq(AnalyticsRepository.COMPLETED_ORDER_STATUSES), any(), any()))
                .thenReturn(projection(new BigDecimal("24500.5"), 14L));

        FinancialHealthResponse response = service.getFinancialHealth("day");

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(analyticsRepository).aggregateFinancialHealth(
                eq(tenantId),
                eq(AnalyticsRepository.COMPLETED_ORDER_STATUSES),
                startCaptor.capture(),
                endCaptor.capture()
        );

        assertThat(startCaptor.getValue()).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));
        assertThat(endCaptor.getValue()).isEqualTo(Instant.parse("2026-11-21T03:00:00Z"));
        assertThat(response.granularity()).isEqualTo("day");
        assertThat(response.revenue()).isEqualByComparingTo("24500.50");
        assertThat(response.revenue().scale()).isEqualTo(2);
        assertThat(response.orderCount()).isEqualTo(14L);
    }

    @Test
    void getFinancialHealth_weekGranularity_calculatesIsoWeekMondayToSunday() {
        mockEstablishmentTimezone("America/Argentina/Buenos_Aires");
        when(analyticsRepository.aggregateFinancialHealth(eq(tenantId), eq(AnalyticsRepository.COMPLETED_ORDER_STATUSES), any(), any()))
                .thenReturn(projection(BigDecimal.ZERO, 0L));

        FinancialHealthResponse response = service.getFinancialHealth("week");

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(analyticsRepository).aggregateFinancialHealth(
                eq(tenantId),
                eq(AnalyticsRepository.COMPLETED_ORDER_STATUSES),
                startCaptor.capture(),
                endCaptor.capture()
        );

        // Week of 2026-11-20 (Fri): Monday 2026-11-16 00:00-03:00 to Monday 2026-11-23 00:00-03:00
        assertThat(startCaptor.getValue()).isEqualTo(Instant.parse("2026-11-16T03:00:00Z"));
        assertThat(endCaptor.getValue()).isEqualTo(Instant.parse("2026-11-23T03:00:00Z"));
        assertThat(response.granularity()).isEqualTo("week");
        assertThat(response.revenue()).isEqualByComparingTo("0.00");
        assertThat(response.revenue().scale()).isEqualTo(2);
        assertThat(response.orderCount()).isEqualTo(0L);
    }

    @Test
    void getFinancialHealth_monthGranularity_calculatesCalendarMonthBounds() {
        mockEstablishmentTimezone("America/Argentina/Buenos_Aires");
        when(analyticsRepository.aggregateFinancialHealth(eq(tenantId), eq(AnalyticsRepository.COMPLETED_ORDER_STATUSES), any(), any()))
                .thenReturn(projection(new BigDecimal("1000.00"), 3L));

        FinancialHealthResponse response = service.getFinancialHealth("month");

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(analyticsRepository).aggregateFinancialHealth(
                eq(tenantId),
                eq(AnalyticsRepository.COMPLETED_ORDER_STATUSES),
                startCaptor.capture(),
                endCaptor.capture()
        );

        // November 2026: 2026-11-01 00:00-03:00 to 2026-12-01 00:00-03:00
        assertThat(startCaptor.getValue()).isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
        assertThat(endCaptor.getValue()).isEqualTo(Instant.parse("2026-12-01T03:00:00Z"));
        assertThat(response.granularity()).isEqualTo("month");
    }

    @Test
    void getFinancialHealth_invalidGranularity_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.getFinancialHealth("year"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid granularity 'year'");
    }

    @Test
    void getFinancialHealth_missingTenantContext_throwsTenantViolationException() {
        TenantContext.clear();
        assertThatThrownBy(() -> service.getFinancialHealth("day"))
                .isInstanceOf(TenantViolationException.class);
    }
}
