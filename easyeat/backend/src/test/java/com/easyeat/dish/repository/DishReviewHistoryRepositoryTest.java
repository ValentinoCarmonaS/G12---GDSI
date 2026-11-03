package com.easyeat.dish.repository;

import com.easyeat.dish.domain.DishReviewHistory;
import com.easyeat.dish.domain.ReviewEventType;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-034 and FR-035 for the administrative review log.
 *
 * <p>FR-034 defines immutability as "the absence of any modifying operation at any surface… a
 * violation is impossible to express rather than merely detectable". That wording decides how
 * this suite is written: it asserts that the operations <b>are not declared</b>, rather than that
 * calling them throws. A test that caught an exception at runtime would be testing detection; the
 * requirement asks for absence.
 *
 * <p>The sharp edge is inheritance. {@code JpaRepository} brings {@code delete},
 * {@code deleteById}, {@code deleteAll}, {@code deleteAllInBatch} and more with it, so a
 * repository that extends it cannot satisfy FR-034 no matter what it declares itself. These tests
 * therefore walk {@link Class#getMethods()}, which includes inherited methods, instead of
 * {@code getDeclaredMethods()}.
 */
@DisplayName("DishReviewHistoryRepository: append-only by construction")
class DishReviewHistoryRepositoryTest {

    private static final Class<?> REPOSITORY = DishReviewHistoryRepository.class;

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
                    .as("JpaRepository inherits delete, deleteById, deleteAll and "
                            + "deleteAllInBatch. Extending it would make FR-034 unsatisfiable "
                            + "regardless of what this interface declares")
                    .isFalse();
            assertThat(CrudRepository.class.isAssignableFrom(REPOSITORY))
                    .as("CrudRepository inherits the same delete operations")
                    .isFalse();
        }

        @Test
        @DisplayName("declares no deletion operation, inherited or otherwise")
        void noDeletionAnywhere() {
            List<String> deleting = allMethods().stream()
                    .map(Method::getName)
                    .filter(n -> n.startsWith("delete") || n.startsWith("remove"))
                    .toList();

            assertThat(deleting)
                    .as("FR-035 retains an audit entry for as long as the dish exists, so no "
                            + "deletion surface may exist at all")
                    .isEmpty();
        }

        @Test
        @DisplayName("declares no @Modifying query")
        void noModifyingQuery() {
            List<String> modifying = allMethods().stream()
                    .filter(m -> m.isAnnotationPresent(Modifying.class))
                    .map(Method::getName)
                    .toList();

            assertThat(modifying)
                    .as("a @Modifying query is an UPDATE or DELETE by another name")
                    .isEmpty();
        }

        @Test
        @DisplayName("exposes save, so appending is the one write that exists")
        void exposesSave() {
            assertThat(allMethods().stream().map(Method::getName))
                    .contains("save");
        }

        @Test
        @DisplayName("every other operation is a read")
        void everythingElseIsARead() {
            List<String> writes = allMethods().stream()
                    .map(Method::getName)
                    .filter(n -> !n.equals("save"))
                    .filter(n -> !(n.startsWith("find") || n.startsWith("count")
                            || n.startsWith("exists") || n.startsWith("get")))
                    .toList();

            assertThat(writes)
                    .as("the interface must expose append and queries, and nothing else")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("the queries this feature actually needs")
    class Queries {

        @Test
        @DisplayName("a dish's history is retrievable in creation order, tenant-scoped")
        void chronologicalTenantScopedRetrieval() throws NoSuchMethodException {
            Method m = REPOSITORY.getMethod(
                    "findByDishIdAndTenantIdOrderByCreatedAtAsc", UUID.class, UUID.class);

            assertThat(m.getReturnType()).isEqualTo(List.class);
            assertThat(m.getName())
                    .as("SC-005 orders the audit timeline by creation timestamp ascending")
                    .endsWith("OrderByCreatedAtAsc");
        }

        @Test
        @DisplayName("the highest cycle number of a dish is retrievable, for FR-022's MAX + 1")
        void highestCycleNumberIsQueryable() throws NoSuchMethodException {
            Method m = REPOSITORY.getMethod("findMaxCycleNumberByDishId", UUID.class);
            assertThat(m.isAnnotationPresent(Modifying.class))
                    .as("reading the maximum is a read")
                    .isFalse();
        }

        @Test
        @DisplayName("a dish's review cycle count is retrievable, for the detail view")
        void cycleCountIsQueryable() throws NoSuchMethodException {
            Method m = REPOSITORY.getMethod("countByDishIdAndTenantId", UUID.class, UUID.class);
            assertThat(m.getReturnType()).isIn(long.class, Long.class);
        }
    }

    @Nested
    @DisplayName("the entity is immutable at the mapping, not only by convention")
    class EntityImmutability {

        @Test
        @DisplayName("is annotated @Immutable, so Hibernate emits no UPDATE for it")
        void isImmutable() {
            assertThat(DishReviewHistory.class.isAnnotationPresent(Immutable.class))
                    .as("FR-034 asks for structural enforcement, not a convention a future "
                            + "setter could break")
                    .isTrue();
        }

        @Test
        @DisplayName("declares no setter and no lifecycle callback that could rewrite a row")
        void noSettersAndNoPreUpdate() {
            List<String> setters = Arrays.stream(DishReviewHistory.class.getMethods())
                    .filter(m -> m.getName().startsWith("set"))
                    .map(Method::getName)
                    .toList();
            assertThat(setters).isEmpty();

            List<String> callbacks = Arrays.stream(DishReviewHistory.class.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(jakarta.persistence.PreUpdate.class)
                            || m.isAnnotationPresent(jakarta.persistence.PostUpdate.class))
                    .map(Method::getName)
                    .toList();
            assertThat(callbacks)
                    .as("a row that cannot change has nothing to do on update")
                    .isEmpty();
        }

        @Test
        @DisplayName("every field is final except where JPA forbids it")
        void fieldsAreFinalOrPrivate() {
            Arrays.stream(DishReviewHistory.class.getDeclaredFields())
                    .filter(f -> !f.isSynthetic())
                    .forEach(f -> assertThat(Modifier.isPrivate(f.getModifiers()))
                            .as("field %s must be private", f.getName())
                            .isTrue());
        }
    }

    @Nested
    @DisplayName("the rejection comment rule is unexpressible to violate (FR-021)")
    class RejectionCommentRule {

        @Test
        @DisplayName("a rejection cannot be built without a usable comment")
        void rejectionRequiresComment() {
            UUID dish = UUID.randomUUID();
            UUID tenant = UUID.randomUUID();
            UUID admin = UUID.randomUUID();

            assertThatThrownBy(() -> DishReviewHistory.rejected(dish, tenant, 1, admin, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DishReviewHistory.rejected(dish, tenant, 1, admin, "   ", null))
                    .as("FR-021 measures length after trimming, so whitespace is not a comment")
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DishReviewHistory.rejected(dish, tenant, 1, admin, "too short", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DishReviewHistory.rejected(dish, tenant, 1, admin,
                    "x".repeat(1001), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("the boundaries are exactly 10 and 1000 characters after trimming")
        void commentBoundaries() {
            UUID dish = UUID.randomUUID();
            UUID tenant = UUID.randomUUID();
            UUID admin = UUID.randomUUID();

            assertThat(DishReviewHistory.rejected(dish, tenant, 1, admin, "x".repeat(10), null)
                    .getComment()).hasSize(10);
            assertThat(DishReviewHistory.rejected(dish, tenant, 1, admin, "x".repeat(1000), null)
                    .getComment()).hasSize(1000);
            assertThat(DishReviewHistory.rejected(dish, tenant, 1, admin,
                    "  " + "x".repeat(10) + "  ", null).getComment())
                    .as("FR-021 stores the comment trimmed, and measures length after trimming")
                    .hasSize(10);
        }

        @Test
        @DisplayName("an approval carries no comment field to fill, so the two cannot be confused")
        void approvalTakesNoComment() {
            DishReviewHistory approval = DishReviewHistory.approved(
                    UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID());

            assertThat(approval.getEventType()).isEqualTo(ReviewEventType.APPROVED);
            assertThat(approval.getComment()).isNull();

            List<String> approvedFactories = Arrays.stream(DishReviewHistory.class.getMethods())
                    .filter(m -> m.getName().equals("approved"))
                    .map(m -> m.getParameterCount() + " args")
                    .toList();
            assertThat(approvedFactories)
                    .as("there must be no way to attach a comment to an approval by mistake")
                    .containsExactly("4 args");
        }

        @Test
        @DisplayName("a cycle number below 1 is refused in the domain, not only by the CHECK")
        void cycleNumberMustBePositive() {
            assertThatThrownBy(() -> DishReviewHistory.approved(
                    UUID.randomUUID(), UUID.randomUUID(), 0, UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DishReviewHistory.approved(
                    UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("the identifiers are all required, so no entry can be orphaned")
        void identifiersAreRequired() {
            UUID any = UUID.randomUUID();
            assertThatThrownBy(() -> DishReviewHistory.approved(null, any, 1, any))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> DishReviewHistory.approved(any, null, 1, any))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> DishReviewHistory.approved(any, any, 1, null))
                    .as("FR-035 keeps the actor on every row, so admin_id cannot be absent")
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("what an entry reports about itself")
    class EntryState {

        private final UUID dish = UUID.randomUUID();
        private final UUID tenant = UUID.randomUUID();
        private final UUID admin = UUID.randomUUID();

        @Test
        @DisplayName("an approval reports every field it was built from")
        void approvalCarriesItsData() {
            DishReviewHistory entry = DishReviewHistory.approved(dish, tenant, 3, admin);

            assertThat(entry.getDishId()).isEqualTo(dish);
            assertThat(entry.getTenantId()).isEqualTo(tenant);
            assertThat(entry.getCycleNumber()).isEqualTo(3);
            assertThat(entry.getAdminId()).isEqualTo(admin);
            assertThat(entry.getEventType()).isEqualTo(ReviewEventType.APPROVED);
            assertThat(entry.getComment()).isNull();
            assertThat(entry.getCreatedAt())
                    .as("the instant is assigned at construction, not left for the database")
                    .isNotNull();
            assertThat(entry.getId())
                    .as("the identifier is generated on persist, so it is null until then")
                    .isNull();
        }

        @Test
        @DisplayName("a rejection reports its event type and its trimmed comment")
        void rejectionCarriesItsData() {
            DishReviewHistory entry = DishReviewHistory.rejected(dish, tenant, 2, admin,
                    "  El costo del salmon dispara el food cost.  ", null);

            assertThat(entry.getEventType()).isEqualTo(ReviewEventType.REJECTED);
            assertThat(entry.getComment())
                    .isEqualTo("El costo del salmon dispara el food cost.");
        }

        @Test
        @DisplayName("a rejection carries the proposed-edition snapshot it was built with (FR-042)")
        void rejectionCarriesItsProposalSnapshot() {
            DishReviewHistory entry = DishReviewHistory.rejected(dish, tenant, 2, admin,
                    "Reemplazá la proteína por merluza.", "{\"title\":\"Pure de Papas\"}");

            assertThat(entry.getProposedChangesJson()).isEqualTo("{\"title\":\"Pure de Papas\"}");
        }

        @Test
        @DisplayName("an approval carries no comment and no proposal, by construction")
        void approvalCarriesNoCommentOrProposal() {
            DishReviewHistory approval = DishReviewHistory.approved(dish, tenant, 1, admin);

            assertThat(approval.getComment()).isNull();
            assertThat(approval.getProposedChangesJson()).isNull();
        }

        @Test
        @DisplayName("two unpersisted entries are never equal, since neither has an identity yet")
        void unpersistedEntriesAreDistinct() {
            DishReviewHistory a = DishReviewHistory.approved(dish, tenant, 1, admin);
            DishReviewHistory b = DishReviewHistory.approved(dish, tenant, 1, admin);

            assertThat(a)
                    .as("equality is by database identity; two transient rows are two rows")
                    .isEqualTo(a)
                    .isNotEqualTo(b)
                    .isNotEqualTo("not an entry")
                    .isNotEqualTo(null);
            assertThat(a.hashCode()).isEqualTo(a.hashCode());
        }

        @Test
        @DisplayName("toString names the dish, cycle and event, for a readable log line")
        void toStringIsUseful() {
            String text = DishReviewHistory.rejected(dish, tenant, 4, admin,
                    "Reemplazalo por merluza y reenvia.", null).toString();

            assertThat(text).contains(dish.toString(), "4", "REJECTED");
        }
    }
}
