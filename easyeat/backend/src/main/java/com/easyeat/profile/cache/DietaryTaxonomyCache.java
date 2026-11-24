package com.easyeat.profile.cache;

import com.easyeat.profile.domain.DietaryRestriction;
import com.easyeat.profile.repository.DietaryRestrictionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bounded Caffeine cache for the platform dietary restriction taxonomy (FR-005, D-05, D-06).
 * Holds at most 1 entry ("ALL") with a 60-minute write TTL.
 */
@Component
public class DietaryTaxonomyCache {

    public static final long MAX_SIZE = 1L;
    public static final long TTL_MINUTES = 60L;
    private static final String CACHE_KEY = "ALL";

    private final DietaryRestrictionRepository repository;
    private final Cache<String, List<DietaryRestriction>> cache;

    public DietaryTaxonomyCache(DietaryRestrictionRepository repository) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_SIZE)
                .expireAfterWrite(Duration.ofMinutes(TTL_MINUTES))
                .build();
    }

    public List<DietaryRestriction> getAll() {
        return cache.get(CACHE_KEY, ignored -> List.copyOf(repository.findAllByOrderByCategoryAscNameAsc()));
    }

    public Map<String, DietaryRestriction> getMapByCode() {
        return getAll().stream()
                .collect(Collectors.toUnmodifiableMap(
                        DietaryRestriction::getCode,
                        Function.identity(),
                        (first, second) -> first
                ));
    }

    public void evictAll() {
        cache.invalidateAll();
    }
}
