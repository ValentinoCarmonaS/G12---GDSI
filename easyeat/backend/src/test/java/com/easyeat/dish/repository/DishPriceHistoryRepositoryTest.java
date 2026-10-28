package com.easyeat.dish.repository;

import com.easyeat.dish.domain.DishPriceHistory;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-034 and FR-035 for the retail price audit trail, plus SC-005's chronological retrieval.
 *
 * <p>Same shape as {@link DishReviewHistoryRepositoryTest} and for the same reason: the absence
 * of a modifying operation is the observable, so the assertions are about what the interface
 * declares rather than about what calling it would do.
 */
@DisplayName("DishPriceHistoryRepository: append-only by construction")
class DishPriceHistoryRepositoryTest {

    private static final Class<?> REPOSITORY = DishPriceHistoryRepository.class;

    private static List<Method> allMethods() {
        return Arrays.asList(REPOSITORY.getMethods());
    }

    @Nested
    @DisplayName("the shape FR-034 requires")
    class AppendOnlyShape {

        @Test
        @DisplayName("is a Spring Data repository interface, but not a JpaRepository")
        void isARepositoryButNotAJpaRepository() {
            assertThat(REPOSITORY.isInterface()).isTrue();
            assertThat(REPOSITORY.isAnnotationPresent(Repository.class)).isTrue();
            assertThat(JpaRepository.class.isAssignableFrom(REPOSITORY))
                    .as("inheriting JpaRepository would inherit a deletion surface")
                    .isFalse();
            assertThat(CrudRepository.class.isAssignableFrom(REPOSITORY)).isFalse();
        }

        @Test
        @DisplayName("declares no deletion operation, inherited or otherwise")
        void noDeletionAnywhere() {
            assertThat(allMethods().stream()
                    .map(Method::getName)
                    .filter(n -> n.startsWith("delete") || n.startsWith("remove"))
                    .toList())
                    .as("FR-035 keeps a price change on record for as long as the dish exists")
                    .isEmpty();
        }

        @Test
        @DisplayName("declares no @Modifying query")
        void noModifyingQuery() {
            assertThat(allMethods().stream()
                    .filter(m -> m.isAnnotationPresent(Modifying.class))
                    .map(Method::getName)
                    .toList())
                    .isEmpty();
        }

        @Test
        @DisplayName("exposes save, and every other operation is a read")
        void savePlusReadsOnly() {
            assertThat(allMethods().stream().map(Method::getName)).contains("save");

            assertThat(allMethods().stream()
                    .map(Method::getName)
                    .filter(n -> !n.equals("save"))
                    .filter(n -> !(n.startsWith("find") || n.startsWith("count")
                            || n.startsWith("exists") || n.startsWith("get")))
                    .toList())
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("chronological retrieval, ascending (SC-005)")
    class ChronologicalRetrieval {

        @Test
        @DisplayName("a dish's price history is retrievable oldest first, tenant-scoped")
        void ascendingByCreatedAt() throws NoSuchMethodException {
            Method m = REPOSITORY.getMethod(
                    "findByDishIdAndTenantIdOrderByCreatedAtAsc", UUID.class, UUID.class);

            assertThat(m.getReturnType()).isEqualTo(List.class);
            assertThat(m.getName())
                    .as("SC-005 specifies creation timestamp ascending, so the order is fixed in "
                            + "the query rather than left to a caller")
                    .endsWith("OrderByCreatedAtAsc");
        }

        @Test
        @DisplayName("the most recent entry is retrievable on its own, for the previous price")
        void latestEntryIsQueryable() throws NoSuchMethodException {
            Method m = REPOSITORY.getMethod(
                    "findFirstByDishIdAndTenantIdOrderByCreatedAtDesc", UUID.class, UUID.class);
            assertThat(m.isAnnotationPresent(Modifying.class)).isFalse();
        }
    }

    @Nested
    @DisplayName("the entity is immutable at the mapping")
    class EntityImmutability {

        @Test
        @DisplayName("is annotated @Immutable and declares no setter")
        void immutableWithoutSetters() {
            assertThat(DishPriceHistory.class.isAnnotationPresent(Immutable.class)).isTrue();

            assertThat(Arrays.stream(DishPriceHistory.class.getMethods())
                    .map(Method::getName)
                    .filter(n -> n.startsWith("set"))
                    .toList())
                    .isEmpty();
        }

        @Test
        @DisplayName("declares no update lifecycle callback")
        void noUpdateCallback() {
            assertThat(Arrays.stream(DishPriceHistory.class.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(jakarta.persistence.PreUpdate.class)
                            || m.isAnnotationPresent(jakarta.persistence.PostUpdate.class))
                    .map(Method::getName)
                    .toList())
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("the invariants the schema also enforces (FR-010, FR-013)")
    class DomainInvariants {

        private static final UUID DISH = UUID.randomUUID();
        private static final UUID TENANT = UUID.randomUUID();
        private static final UUID ADMIN = UUID.randomUUID();

        @Test
        @DisplayName("a first pricing carries no previous price, and that is not missing data")
        void firstPricingHasNullPreviousPrice() {
            DishPriceHistory entry = DishPriceHistory.record(
                    DISH, TENANT, ADMIN, null, new BigDecimal("11250.00"),
                    new BigDecimal("4500.00"));

            assertThat(entry.getPreviousPrice())
                    .as("FR-010: null on the first pricing of a dish is a fact about the dish")
                    .isNull();
            assertThat(entry.getNewPrice()).isEqualByComparingTo("11250.00");
        }

        @Test
        @DisplayName("a new price of zero or below is refused in the domain")
        void newPriceMustBePositive() {
            assertThatThrownBy(() -> DishPriceHistory.record(DISH, TENANT, ADMIN, null,
                    BigDecimal.ZERO, new BigDecimal("4500.00")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DishPriceHistory.record(DISH, TENANT, ADMIN, null,
                    new BigDecimal("-1.00"), new BigDecimal("4500.00")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a Food Cost of 0.00 is legitimate, but a negative one is not")
        void foodCostSnapshotMayBeZeroButNotNegative() {
            assertThat(DishPriceHistory.record(DISH, TENANT, ADMIN, null,
                    new BigDecimal("11250.00"), BigDecimal.ZERO).getFoodCostSnapshot())
                    .as("0.00 is what a recipe whose every ingredient is unpriced costs")
                    .isEqualByComparingTo("0.00");

            assertThatThrownBy(() -> DishPriceHistory.record(DISH, TENANT, ADMIN, null,
                    new BigDecimal("11250.00"), new BigDecimal("-0.01")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("money is carried at scale 2, because the Food Cost already arrives that way")
        void moneyIsScaleTwo() {
            DishPriceHistory entry = DishPriceHistory.record(
                    DISH, TENANT, ADMIN, new BigDecimal("10800.00"), new BigDecimal("11250.00"),
                    new BigDecimal("4500.00"));

            assertThat(entry.getNewPrice().scale()).isEqualTo(2);
            assertThat(entry.getFoodCostSnapshot().scale())
                    .as("spec 006 rounds each line subtotal to scale 2, so this feature stores "
                            + "the value without rounding it again")
                    .isEqualTo(2);
            assertThat(entry.getPreviousPrice().scale()).isEqualTo(2);
        }
    }
}
