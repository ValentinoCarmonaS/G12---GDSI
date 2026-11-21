package com.easyeat.analytics.service;

import com.easyeat.analytics.dto.DishRankingItemResponse;
import com.easyeat.analytics.repository.AnalyticsRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.common.exception.TenantViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Service
public class DishRankingService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final AnalyticsRepository analyticsRepository;
    private final EstablishmentRepository establishmentRepository;
    private final Clock clock;

    @Autowired
    public DishRankingService(
            AnalyticsRepository analyticsRepository,
            EstablishmentRepository establishmentRepository
    ) {
        this(analyticsRepository, establishmentRepository, Clock.systemUTC());
    }

    public DishRankingService(
            AnalyticsRepository analyticsRepository,
            EstablishmentRepository establishmentRepository,
            Clock clock
    ) {
        this.analyticsRepository = analyticsRepository;
        this.establishmentRepository = establishmentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DishRankingItemResponse> getDishRanking(String granularity) {
        String effectiveGranularity = granularity == null ? "all" : granularity;
        if (!effectiveGranularity.equals("all")
                && !effectiveGranularity.equals("day")
                && !effectiveGranularity.equals("week")
                && !effectiveGranularity.equals("month")) {
            throw new IllegalArgumentException(
                    "Invalid granularity '" + granularity + "'. Supported values are: all, day, week, month"
            );
        }

        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new TenantViolationException("Missing active establishment context");
        }

        if (effectiveGranularity.equals("all")) {
            return analyticsRepository.findDishRankingAll(
                            tenantId,
                            AnalyticsRepository.COMPLETED_ORDER_STATUSES
                    ).stream()
                    .map(p -> new DishRankingItemResponse(p.getDishId(), p.getDishName(), p.getUnitsSold()))
                    .toList();
        }

        ZoneId zone = establishmentRepository.findById(tenantId)
                .map(Establishment::getTimezone)
                .filter(tz -> tz != null && !tz.isBlank())
                .map(ZoneId::of)
                .orElse(DEFAULT_ZONE);

        LocalDate today = ZonedDateTime.now(clock).withZoneSameInstant(zone).toLocalDate();
        ZonedDateTime startZoned;
        ZonedDateTime exclusiveEndZoned;

        switch (effectiveGranularity) {
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
            default -> throw new IllegalStateException("Unexpected granularity: " + effectiveGranularity);
        }

        return analyticsRepository.findDishRanking(
                        tenantId,
                        AnalyticsRepository.COMPLETED_ORDER_STATUSES,
                        startZoned.toInstant(),
                        exclusiveEndZoned.toInstant()
                ).stream()
                .map(p -> new DishRankingItemResponse(p.getDishId(), p.getDishName(), p.getUnitsSold()))
                .toList();
    }
}
