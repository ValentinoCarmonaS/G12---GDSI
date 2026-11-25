package com.easyeat.operations.cache;

import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded Caffeine cache for weekly establishment operating schedules (FR-005, D-05, D-06).
 * Holds up to 200 establishments with a 15-minute write TTL and synchronous eviction
 * reinforced by a JPA entity listener.
 */
@Component
public class OperatingScheduleCache {

    public static final long MAX_SIZE = 200L;
    public static final long TTL_MINUTES = 15L;

    private static volatile OperatingScheduleCache activeInstance;

    private final OperatingScheduleRepository repository;
    private final Cache<UUID, Map<DayOfWeek, EstablishmentOperatingSchedule>> cache;

    public OperatingScheduleCache(OperatingScheduleRepository repository) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_SIZE)
                .expireAfterWrite(Duration.ofMinutes(TTL_MINUTES))
                .build();
        activeInstance = this;
    }

    public Map<DayOfWeek, EstablishmentOperatingSchedule> getWeekMap(UUID establishmentId) {
        if (establishmentId == null) {
            return Collections.emptyMap();
        }
        return cache.get(establishmentId, id -> {
            List<EstablishmentOperatingSchedule> rows = repository.findByEstablishmentId(id);
            Map<DayOfWeek, EstablishmentOperatingSchedule> byDay = new EnumMap<>(DayOfWeek.class);
            for (EstablishmentOperatingSchedule row : rows) {
                byDay.put(row.getDayOfWeek(), row);
            }
            return Collections.unmodifiableMap(byDay);
        });
    }

    public List<EstablishmentOperatingSchedule> getWeekList(UUID establishmentId) {
        return getWeekMap(establishmentId).values().stream()
                .sorted(Comparator.comparing(EstablishmentOperatingSchedule::getDayOfWeek))
                .toList();
    }

    public void evict(UUID establishmentId) {
        if (establishmentId != null) {
            cache.invalidate(establishmentId);
        }
    }

    public void evictAll() {
        cache.invalidateAll();
    }

    /**
     * JPA entity listener safety net ensuring direct repository writes evict cached schedules.
     */
    public static class ScheduleEntityListener {

        @PostPersist
        @PostUpdate
        @PostRemove
        public void onScheduleChange(EstablishmentOperatingSchedule schedule) {
            OperatingScheduleCache instance = activeInstance;
            if (instance != null && schedule != null) {
                instance.evict(schedule.getEstablishmentId());
            }
        }
    }
}
