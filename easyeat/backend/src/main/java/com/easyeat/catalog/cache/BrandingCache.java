package com.easyeat.catalog.cache;

import com.easyeat.catalog.domain.RestaurantBranding;
import com.easyeat.catalog.dto.RestaurantBrandingResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Bounded Caffeine cache for establishment branding responses across DINER and ADMIN views (FR-005, D-05, D-06).
 * Holds up to 200 entries with a 15-minute write TTL and synchronous eviction of both views
 * reinforced by a JPA entity listener.
 */
@Component
public class BrandingCache {

    public static final long MAX_SIZE = 200L;
    public static final long TTL_MINUTES = 15L;

    private static volatile BrandingCache activeInstance;

    private final Cache<BrandingCacheKey, RestaurantBrandingResponse> cache;

    public enum BrandingViewType {
        DINER,
        ADMIN
    }

    public record BrandingCacheKey(BrandingViewType view, UUID establishmentId) {
        public BrandingCacheKey {
            Objects.requireNonNull(view, "view must not be null");
            Objects.requireNonNull(establishmentId, "establishmentId must not be null");
        }
    }

    public BrandingCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_SIZE)
                .expireAfterWrite(Duration.ofMinutes(TTL_MINUTES))
                .build();
        activeInstance = this;
    }

    public RestaurantBrandingResponse getOrLoad(
            BrandingViewType view,
            UUID establishmentId,
            Supplier<RestaurantBrandingResponse> loader
    ) {
        if (view == null || establishmentId == null) {
            return loader.get();
        }
        return cache.get(new BrandingCacheKey(view, establishmentId), key -> loader.get());
    }

    public void evict(UUID establishmentId) {
        if (establishmentId != null) {
            cache.invalidate(new BrandingCacheKey(BrandingViewType.DINER, establishmentId));
            cache.invalidate(new BrandingCacheKey(BrandingViewType.ADMIN, establishmentId));
        }
    }

    public void evictAll() {
        cache.invalidateAll();
    }

    /**
     * JPA entity listener safety net ensuring direct repository writes evict both cached branding views.
     */
    public static class BrandingEntityListener {

        @PostPersist
        @PostUpdate
        @PostRemove
        public void onBrandingChange(RestaurantBranding branding) {
            BrandingCache instance = activeInstance;
            if (instance != null && branding != null) {
                instance.evict(branding.getEstablishmentId());
            }
        }
    }
}
