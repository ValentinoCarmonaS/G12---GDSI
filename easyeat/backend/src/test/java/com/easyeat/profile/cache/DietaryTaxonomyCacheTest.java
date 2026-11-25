package com.easyeat.profile.cache;

import com.easyeat.profile.domain.DietaryCategory;
import com.easyeat.profile.domain.DietaryRestriction;
import com.easyeat.profile.repository.DietaryRestrictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 028 T006: unit tests for DietaryTaxonomyCache (FR-005, D-05, D-06).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Spec 028 - DietaryTaxonomyCache unit tests (T006)")
class DietaryTaxonomyCacheTest {

    @Mock
    private DietaryRestrictionRepository repository;

    private DietaryTaxonomyCache cache;

    @BeforeEach
    void setUp() {
        cache = new DietaryTaxonomyCache(repository);
    }

    @Test
    @DisplayName("enforces bounded Caffeine policy: maximumSize=1 and expireAfterWrite=60 minutes")
    void shouldExposeBoundedPolicy() {
        assertThat(DietaryTaxonomyCache.MAX_SIZE).isEqualTo(1L);
        assertThat(DietaryTaxonomyCache.TTL_MINUTES).isEqualTo(60L);
    }

    @Test
    @DisplayName("caches taxonomy in memory on first load and avoids second repository call")
    void shouldReuseCachedTaxonomyWithoutSecondRepositoryCall() {
        List<DietaryRestriction> taxonomy = List.of(
                new DietaryRestriction("GLUTEN", "Gluten", DietaryCategory.COMMON_ALLERGEN, "Contains gluten"),
                new DietaryRestriction("VEGAN", "Vegano", DietaryCategory.DIETARY_LIFESTYLE, "Plant based")
        );
        when(repository.findAllByOrderByCategoryAscNameAsc()).thenReturn(taxonomy);

        List<DietaryRestriction> first = cache.getAll();
        List<DietaryRestriction> second = cache.getAll();
        Map<String, DietaryRestriction> asMap = cache.getMapByCode();

        assertThat(first).hasSize(2);
        assertThat(second).isEqualTo(first);
        assertThat(asMap).containsKeys("GLUTEN", "VEGAN");
        assertThatThrownBy(() -> first.add(new DietaryRestriction("X", "X", DietaryCategory.COMMON_ALLERGEN, "X")))
                .isInstanceOf(UnsupportedOperationException.class);
        verify(repository, times(1)).findAllByOrderByCategoryAscNameAsc();
    }

    @Test
    @DisplayName("evictAll forces reload from repository on subsequent read")
    void evictAllShouldForceReload() {
        when(repository.findAllByOrderByCategoryAscNameAsc()).thenReturn(List.of(
                new DietaryRestriction("VEGAN", "Vegano", DietaryCategory.DIETARY_LIFESTYLE, "Plant based")
        ));

        cache.getAll();
        cache.evictAll();
        cache.getAll();

        verify(repository, times(2)).findAllByOrderByCategoryAscNameAsc();
    }
}
