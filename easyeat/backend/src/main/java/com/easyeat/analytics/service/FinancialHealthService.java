package com.easyeat.analytics.service;

import com.easyeat.analytics.dto.FinancialHealthResponse;
import com.easyeat.analytics.repository.AnalyticsRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.common.exception.TenantViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

@Service
public class FinancialHealthService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final AnalyticsRepository analyticsRepository;
    private final EstablishmentRepository establishmentRepository;
    private final Clock clock;

    @Autowired
    public FinancialHealthService(
            AnalyticsRepository analyticsRepository,
            EstablishmentRepository establishmentRepository
    ) {
        this(analyticsRepository, establishmentRepository, Clock.systemUTC());
    }

    public FinancialHealthService(
            AnalyticsRepository analyticsRepository,
            EstablishmentRepository establishmentRepository,
            Clock clock
    ) {
        this.analyticsRepository = analyticsRepository;
        this.establishmentRepository = establishmentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinancialHealthResponse getFinancialHealth(String granularity) {
        if (granularity == null || (!granularity.equals("day") && !granularity.equals("week") && !granularity.equals("month"))) {
            throw new IllegalArgumentException("Invalid granularity '" + granularity + "'. Supported values are: day, week, month");
        }

        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new TenantViolationException("Missing active establishment context");
        }

        ZoneId zone = establishmentRepository.findById(tenantId)
                .map(Establishment::getTimezone)
                .filter(tz -> tz != null && !tz.isBlank())
                .map(ZoneId::of)
                .orElse(DEFAULT_ZONE);

        LocalDate today = ZonedDateTime.now(clock).withZoneSameInstant(zone).toLocalDate();

        ZonedDateTime startZoned;
        ZonedDateTime exclusiveEndZoned;

        switch (granularity) {
            case "day" -> {
                startZoned = today.atStartOfDay(zone);
                exclusiveEndZoned = today.plusDays(1).atStartOfDay(zone);
            }
            case "week" -> {
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                startZoned = monday.atStartOfDay(zone);
                exclusiveEndZoned = monday.plusWeeks(1).atStartOfDay(zone);
            }
            case "month" -> {
                LocalDate firstOfMonth = today.withDayOfMonth(1);
                startZoned = firstOfMonth.atStartOfDay(zone);
                exclusiveEndZoned = firstOfMonth.plusMonths(1).atStartOfDay(zone);
            }
            default -> throw new IllegalArgumentException("Invalid granularity '" + granularity + "'. Supported values are: day, week, month");
        }

        Instant startInstant = startZoned.toInstant();
        Instant endInstant = exclusiveEndZoned.toInstant();

        AnalyticsRepository.FinancialHealthProjection projection = analyticsRepository.aggregateFinancialHealth(
                tenantId,
                AnalyticsRepository.COMPLETED_ORDER_STATUSES,
                startInstant,
                endInstant
        );

        BigDecimal revenue = (projection != null && projection.getRevenue() != null)
                ? projection.getRevenue().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        long orderCount = (projection != null && projection.getOrderCount() != null)
                ? projection.getOrderCount()
                : 0L;

        ZonedDateTime inclusiveEndZoned = exclusiveEndZoned.minusNanos(1);
        if (inclusiveEndZoned.toLocalTime().equals(LocalTime.MAX)) {
            // already 23:59:59.999999999
        }

        return new FinancialHealthResponse(
                granularity,
                startZoned.toOffsetDateTime(),
                inclusiveEndZoned.toOffsetDateTime(),
                revenue,
                orderCount
        );
    }
}
