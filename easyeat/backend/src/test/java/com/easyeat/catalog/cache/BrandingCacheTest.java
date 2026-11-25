package com.easyeat.catalog.cache;

import com.easyeat.catalog.dto.RestaurantBrandingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BrandingCacheTest (T054, FR-005, SC-005, D-05, D-06)")
class BrandingCacheTest {

    private BrandingCache brandingCache;

    @BeforeEach
    void setUp() {
        brandingCache = new BrandingCache();
    }

    @Test
    @DisplayName("Exposes bounded Caffeine limits (MAX_SIZE=200, TTL_MINUTES=15)")
    void shouldExposeBoundedConfigurationConstants() {
        assertThat(BrandingCache.MAX_SIZE).isEqualTo(200L);
        assertThat(BrandingCache.TTL_MINUTES).isEqualTo(15L);
    }

    @Test
    @DisplayName("Caches DINER and ADMIN views separately per establishmentId and evicts both synchronously")
    void shouldCacheDinerAndAdminViewsSeparatelyAndEvictBoth() {
        UUID establishmentId = UUID.randomUUID();
        AtomicInteger dinerLoads = new AtomicInteger(0);
        AtomicInteger adminLoads = new AtomicInteger(0);

        RestaurantBrandingResponse dinerResponse = new RestaurantBrandingResponse(
                establishmentId, "Bistró", "Desc", "/api/v1/establishments/" + establishmentId + "/logo",
                "#10B981", "#065F46", "#F59E0B", "#FFFFFF", "#FFFFFF", "#000000", Instant.now(), false
        );
        RestaurantBrandingResponse adminResponse = new RestaurantBrandingResponse(
                establishmentId, "Bistró Admin", "Desc", "/api/v1/establishments/" + establishmentId + "/logo",
                "#10B981", "#065F46", "#F59E0B", "#FFFFFF", "#FFFFFF", "#000000", Instant.now(), false
        );

        RestaurantBrandingResponse d1 = brandingCache.getOrLoad(
                BrandingCache.BrandingViewType.DINER, establishmentId, () -> {
                    dinerLoads.incrementAndGet();
                    return dinerResponse;
                });
        RestaurantBrandingResponse d2 = brandingCache.getOrLoad(
                BrandingCache.BrandingViewType.DINER, establishmentId, () -> {
                    dinerLoads.incrementAndGet();
                    return dinerResponse;
                });
        RestaurantBrandingResponse a1 = brandingCache.getOrLoad(
                BrandingCache.BrandingViewType.ADMIN, establishmentId, () -> {
                    adminLoads.incrementAndGet();
                    return adminResponse;
                });
        RestaurantBrandingResponse a2 = brandingCache.getOrLoad(
                BrandingCache.BrandingViewType.ADMIN, establishmentId, () -> {
                    adminLoads.incrementAndGet();
                    return adminResponse;
                });

        assertThat(d1).isEqualTo(dinerResponse);
        assertThat(d2).isEqualTo(dinerResponse);
        assertThat(a1).isEqualTo(adminResponse);
        assertThat(a2).isEqualTo(adminResponse);
        assertThat(dinerLoads.get()).isEqualTo(1);
        assertThat(adminLoads.get()).isEqualTo(1);

        brandingCache.evict(establishmentId);

        brandingCache.getOrLoad(BrandingCache.BrandingViewType.DINER, establishmentId, () -> {
            dinerLoads.incrementAndGet();
            return dinerResponse;
        });
        brandingCache.getOrLoad(BrandingCache.BrandingViewType.ADMIN, establishmentId, () -> {
            adminLoads.incrementAndGet();
            return adminResponse;
        });

        assertThat(dinerLoads.get()).isEqualTo(2);
        assertThat(adminLoads.get()).isEqualTo(2);
    }
}
