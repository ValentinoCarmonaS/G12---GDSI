package com.easyeat.operations.cache;

import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 028 T007: unit tests for OperatingScheduleCache and JPA entity listener (FR-005, D-05, D-06).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Spec 028 - OperatingScheduleCache unit tests (T007)")
class OperatingScheduleCacheTest {

    @Mock
    private OperatingScheduleRepository repository;

    private OperatingScheduleCache cache;

    @BeforeEach
    void setUp() {
        cache = new OperatingScheduleCache(repository);
    }

    @Test
    @DisplayName("enforces bounded Caffeine policy: maximumSize=200 and expireAfterWrite=15 minutes")
    void shouldExposeBoundedPolicy() {
        assertThat(OperatingScheduleCache.MAX_SIZE).isEqualTo(200L);
        assertThat(OperatingScheduleCache.TTL_MINUTES).isEqualTo(15L);
    }

    @Test
    @DisplayName("loads weekly schedule once per establishmentId and serves subsequent reads from memory")
    void shouldLoadOncePerEstablishmentAndReuseFromCache() {
        UUID estId = UUID.randomUUID();
        EstablishmentOperatingSchedule monday = new EstablishmentOperatingSchedule(
                estId, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), false
        );
        when(repository.findByEstablishmentId(estId)).thenReturn(List.of(monday));

        Map<DayOfWeek, EstablishmentOperatingSchedule> first = cache.getWeekMap(estId);
        Map<DayOfWeek, EstablishmentOperatingSchedule> second = cache.getWeekMap(estId);
        List<EstablishmentOperatingSchedule> ordered = cache.getWeekList(estId);

        assertThat(first).containsKey(DayOfWeek.MONDAY);
        assertThat(second).isEqualTo(first);
        assertThat(ordered).hasSize(1);
        verify(repository, times(1)).findByEstablishmentId(estId);
    }

    @Test
    @DisplayName("evict invalidates synchronously only the targeted establishmentId")
    void evictShouldInvalidateOnlyTargetEstablishment() {
        UUID estA = UUID.randomUUID();
        UUID estB = UUID.randomUUID();
        when(repository.findByEstablishmentId(estA)).thenReturn(List.of());
        when(repository.findByEstablishmentId(estB)).thenReturn(List.of());

        cache.getWeekMap(estA);
        cache.getWeekMap(estB);

        cache.evict(estA);

        cache.getWeekMap(estA);
        cache.getWeekMap(estB);

        verify(repository, times(2)).findByEstablishmentId(estA);
        verify(repository, times(1)).findByEstablishmentId(estB);
    }

    @Test
    @DisplayName("JPA entity listener evicts establishment schedule on persist/update/remove")
    void jpaListenerShouldEvictEstablishmentSchedule() {
        UUID estId = UUID.randomUUID();
        EstablishmentOperatingSchedule schedule = new EstablishmentOperatingSchedule(
                estId, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(20, 0), false
        );
        when(repository.findByEstablishmentId(estId)).thenReturn(List.of(schedule));

        cache.getWeekMap(estId);
        OperatingScheduleCache.ScheduleEntityListener listener = new OperatingScheduleCache.ScheduleEntityListener();
        listener.onScheduleChange(schedule);
        cache.getWeekMap(estId);

        verify(repository, times(2)).findByEstablishmentId(estId);
    }
}
