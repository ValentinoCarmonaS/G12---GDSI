package com.easyeat.dish.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.DishRejectionCount;
import com.easyeat.dish.dto.DishReviewInboxItemResponse;
import com.easyeat.dish.dto.DishReviewInboxPageResponse;
import com.easyeat.dish.dto.InboxTab;
import com.easyeat.dish.repository.DishReviewHistoryRepository;
import com.easyeat.dish.repository.DishReviewInboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * FR-002 through FR-005 and FR-036 for the administrative review inbox.
 *
 * <p>A unit test over mocks, so "query count" is measured as <b>repository invocations</b> rather
 * than as SQL statements. That is the right level for the defect the requirement targets: a
 * service that asks the history repository once per dish is an N+1 whatever SQL results, and a
 * service whose invocation count is flat in the page size cannot be one. The SQL-level count
 * belongs to {@code DishReviewInboxIntegrationTest} in Phase 7.
 */
@DisplayName("DishReviewInboxService")
class DishReviewInboxServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    private DishReviewInboxRepository inboxRepository;
    private DishReviewHistoryRepository reviewHistoryRepository;
    private UserRepository userRepository;
    private DishReviewInboxService service;

    @BeforeEach
    void setUp() {
        inboxRepository = Mockito.mock(DishReviewInboxRepository.class);
        reviewHistoryRepository = Mockito.mock(DishReviewHistoryRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        service = new DishReviewInboxService(
                inboxRepository, reviewHistoryRepository, userRepository);

        TenantContextHolder.setTenantId(TENANT);

        when(inboxRepository.findPendingInbox(any(), any())).thenReturn(Page.empty());
        when(inboxRepository.findPublishedInbox(any(), any())).thenReturn(Page.empty());
        when(inboxRepository.findReviewedInbox(any(), any())).thenReturn(Page.empty());
        when(inboxRepository.countPending(any())).thenReturn(0L);
        when(reviewHistoryRepository.countRejectionsByDishIds(any(), anyList()))
                .thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ------------------------------------------------------------- fixtures

    private static Dish dish(String name, DishStatus status, Instant submittedAt, UUID author) {
        Dish d = new Dish();
        d.setId(UUID.randomUUID());
        d.setTenantId(TENANT);
        d.setName(name);
        d.setCategory(DishCategory.PLATOS_PRINCIPALES);
        d.setStatus(status);
        d.setSubmittedAt(submittedAt);
        d.setCreatedBy(author);
        return d;
    }

    private static User user(UUID id, String fullName, UserStatus status) {
        User u = new User();
        u.setId(id);
        u.setFullName(fullName);
        u.setStatus(status);
        return u;
    }

    private static Page<Dish> pageOf(List<Dish> dishes, Pageable pageable, long total) {
        return new PageImpl<>(dishes, pageable, total);
    }

    // ------------------------------------------------------------- tests

    @Nested
    @DisplayName("tab filtering: three views that are not a partition (FR-002)")
    class TabFiltering {

        @Test
        @DisplayName("PENDING reads the pending query and no other")
        void pendingTab() {
            service.getInbox(InboxTab.PENDING, 0, 20);

            Mockito.verify(inboxRepository).findPendingInbox(eq(TENANT), any());
            Mockito.verify(inboxRepository, Mockito.never()).findPublishedInbox(any(), any());
            Mockito.verify(inboxRepository, Mockito.never()).findReviewedInbox(any(), any());
        }

        @Test
        @DisplayName("PUBLISHED reads the published query and no other")
        void publishedTab() {
            service.getInbox(InboxTab.PUBLISHED, 0, 20);

            Mockito.verify(inboxRepository).findPublishedInbox(eq(TENANT), any());
            Mockito.verify(inboxRepository, Mockito.never()).findPendingInbox(any(), any());
        }

        @Test
        @DisplayName("HISTORY reads the review-history query, which selects by history not status")
        void historyTab() {
            service.getInbox(InboxTab.HISTORY, 0, 20);

            Mockito.verify(inboxRepository).findReviewedInbox(eq(TENANT), any());
            Mockito.verify(inboxRepository, Mockito.never()).findPendingInbox(any(), any());
            Mockito.verify(inboxRepository, Mockito.never()).findPublishedInbox(any(), any());
        }

        @Test
        @DisplayName("HISTORY may return a PUBLISHED dish: the views overlap on purpose")
        void historyOverlapsPublished() {
            Dish republished = dish("Risotto", DishStatus.PUBLISHED, Instant.now(), null);
            when(inboxRepository.findReviewedInbox(eq(TENANT), any()))
                    .thenReturn(pageOf(List.of(republished), PageRequest.of(0, 20), 1));

            DishReviewInboxPageResponse response = service.getInbox(InboxTab.HISTORY, 0, 20);

            assertThat(response.items())
                    .as("FR-002: a dish rejected once and later published appears in both views, "
                            + "and the service must not filter it out to make them disjoint")
                    .singleElement()
                    .extracting(DishReviewInboxItemResponse::status)
                    .isEqualTo(DishStatus.PUBLISHED);
        }

        @ParameterizedTest
        @EnumSource(InboxTab.class)
        @DisplayName("the pending badge is returned on every tab (FR-003)")
        void pendingCountOnEveryTab(InboxTab tab) {
            when(inboxRepository.countPending(TENANT)).thenReturn(37L);

            assertThat(service.getInbox(tab, 0, 20).pendingCount())
                    .as("the badge counts dishes awaiting decision wherever the Administrator is")
                    .isEqualTo(37L);
        }
    }

    @Nested
    @DisplayName("FIFO ordering (FR-003)")
    class Ordering {

        @Test
        @DisplayName("the pending page is requested with submitted_at ascending")
        void fifoBySubmittedAt() {
            service.getInbox(InboxTab.PENDING, 0, 20);

            Pageable requested = captureInboxPageable();
            assertThat(requested.getSort().getOrderFor("submittedAt"))
                    .as("FR-003 orders oldest first by submission timestamp")
                    .isNotNull()
                    .satisfies(order -> assertThat(order.isAscending()).isTrue());
        }

        @Test
        @DisplayName("no creation-time fallback exists: R-005-1 withdrew it explicitly")
        void noCreatedAtFallback() {
            service.getInbox(InboxTab.PENDING, 0, 20);

            Pageable requested = captureInboxPageable();
            assertThat(requested.getSort().getOrderFor("createdAt"))
                    .as("plan.md R-005-1: the degraded created_at ordering is withdrawn and NOT "
                            + "authorised. submitted_at is written by spec 005 on every transition")
                    .isNull();
            assertThat(requested.getSort().getOrderFor("updatedAt")).isNull();
        }

        @Test
        @DisplayName("the service preserves the order the query returned, rather than re-sorting")
        void preservesQueryOrder() {
            Instant oldest = Instant.parse("2026-10-01T10:00:00Z");
            Dish first = dish("Alfajor", DishStatus.PENDING_PRICE, oldest, null);
            Dish second = dish("Budin", DishStatus.PENDING_PRICE, oldest.plusSeconds(60), null);
            when(inboxRepository.findPendingInbox(eq(TENANT), any()))
                    .thenReturn(pageOf(List.of(first, second), PageRequest.of(0, 20), 2));

            DishReviewInboxPageResponse response = service.getInbox(InboxTab.PENDING, 0, 20);

            assertThat(response.items())
                    .extracting(DishReviewInboxItemResponse::name)
                    .containsExactly("Alfajor", "Budin");
        }

        @Test
        @DisplayName("the published page is requested with published_at descending")
        void publishedNewestFirst() {
            service.getInbox(InboxTab.PUBLISHED, 0, 20);

            Pageable requested = captureInboxPageable();
            assertThat(requested.getSort().getOrderFor("publishedAt"))
                    .isNotNull()
                    .satisfies(order -> assertThat(order.isDescending()).isTrue());
        }

        private Pageable captureInboxPageable() {
            var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            Mockito.verify(inboxRepository, Mockito.atLeastOnce())
                    .findPendingInbox(any(), captor.capture());
            return captor.getValue();
        }
    }

    @Nested
    @DisplayName("resubmission derivation (FR-004)")
    class Resubmission {

        @Test
        @DisplayName("a dish with no past rejection is an initial submission")
        void initialSubmission() {
            Dish d = dish("Tarta", DishStatus.PENDING_PRICE, Instant.now(), null);
            when(inboxRepository.findPendingInbox(eq(TENANT), any()))
                    .thenReturn(pageOf(List.of(d), PageRequest.of(0, 20), 1));

            DishReviewInboxItemResponse item =
                    service.getInbox(InboxTab.PENDING, 0, 20).items().getFirst();

            assertThat(item.isResubmission()).isFalse();
            assertThat(item.reviewCycleCount()).isZero();
        }

        @Test
        @DisplayName("a dish with past rejections is a resubmission, with the count")
        void resubmissionWithCount() {
            Dish d = dish("Tarta", DishStatus.PENDING_PRICE, Instant.now(), null);
            when(inboxRepository.findPendingInbox(eq(TENANT), any()))
                    .thenReturn(pageOf(List.of(d), PageRequest.of(0, 20), 1));
            when(reviewHistoryRepository.countRejectionsByDishIds(eq(TENANT), anyList()))
                    .thenReturn(List.of(new DishRejectionCount(d.getId(), 2L)));

            DishReviewInboxItemResponse item =
                    service.getInbox(InboxTab.PENDING, 0, 20).items().getFirst();

            assertThat(item.isResubmission()).isTrue();
            assertThat(item.reviewCycleCount())
                    .as("the count is PAST rejections; the current cycle is this plus one")
                    .isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("author attribution, including the degraded cases (FR-004, FR-026, FR-027)")
    class AuthorAttribution {

        @Test
        @DisplayName("a dish with no recorded author reports a null name, not a placeholder")
        void noAuthor() {
            Dish d = dish("Tarta", DishStatus.PENDING_PRICE, Instant.now(), null);
            when(inboxRepository.findPendingInbox(eq(TENANT), any()))
                    .thenReturn(pageOf(List.of(d), PageRequest.of(0, 20), 1));

            DishReviewInboxItemResponse item =
                    service.getInbox(InboxTab.PENDING, 0, 20).items().getFirst();

            assertThat(item.authorName())
                    .as("the copy 'Autor no registrado' is the interface's business, not the "
                            + "service's: the API reports absence as null")
                    .isNull();
            assertThat(item.authorDeactivated()).isFalse();
        }

        @Test
        @DisplayName("a suspended author is flagged, and the dish stays fully actionable")
        void suspendedAuthor() {
            UUID authorId = UUID.randomUUID();
            Dish d = dish("Tarta", DishStatus.PENDING_PRICE, Instant.now(), authorId);
            User suspended = user(authorId, "Camila Duarte", UserStatus.SUSPENDED);

            when(inboxRepository.findPendingInbox(eq(TENANT), any()))
                    .thenReturn(pageOf(List.of(d), PageRequest.of(0, 20), 1));
            when(userRepository.findAllById(any())).thenReturn(List.of(suspended));

            DishReviewInboxItemResponse item =
                    service.getInbox(InboxTab.PENDING, 0, 20).items().getFirst();

            assertThat(item.authorName()).isEqualTo("Camila Duarte");
            assertThat(item.authorDeactivated())
                    .as("FR-026: informational only, it never blocks an administrative action")
                    .isTrue();
        }

        @Test
        @DisplayName("an active author is not flagged")
        void activeAuthor() {
            UUID authorId = UUID.randomUUID();
            Dish d = dish("Tarta", DishStatus.PENDING_PRICE, Instant.now(), authorId);
            User active = user(authorId, "Camila Duarte", UserStatus.ACTIVE);

            when(inboxRepository.findPendingInbox(eq(TENANT), any()))
                    .thenReturn(pageOf(List.of(d), PageRequest.of(0, 20), 1));
            when(userRepository.findAllById(any())).thenReturn(List.of(active));

            assertThat(service.getInbox(InboxTab.PENDING, 0, 20).items().getFirst()
                    .authorDeactivated()).isFalse();
        }

        @Test
        @DisplayName("an author row that has vanished degrades to a null name, it does not throw")
        void missingAuthorRow() {
            Dish d = dish("Tarta", DishStatus.PENDING_PRICE, Instant.now(), UUID.randomUUID());
            when(inboxRepository.findPendingInbox(eq(TENANT), any()))
                    .thenReturn(pageOf(List.of(d), PageRequest.of(0, 20), 1));
            when(userRepository.findAllById(any())).thenReturn(List.of());

            assertThat(service.getInbox(InboxTab.PENDING, 0, 20).items().getFirst().authorName())
                    .as("a dangling created_by must not make the whole inbox unreadable")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("no N+1: the repository invocation count is flat in the page size")
    class QueryCount {

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 20, 100})
        @DisplayName("the same number of repository calls is made whatever the page holds")
        void invocationCountIsIndependentOfPageSize(int size) {
            List<Dish> dishes = new java.util.ArrayList<>();
            for (int i = 0; i < size; i++) {
                dishes.add(dish("Plato " + i, DishStatus.PENDING_PRICE,
                        Instant.now().plusSeconds(i), UUID.randomUUID()));
            }
            when(inboxRepository.findPendingInbox(eq(TENANT), any()))
                    .thenReturn(pageOf(dishes, PageRequest.of(0, Math.max(size, 1)), size));

            service.getInbox(InboxTab.PENDING, 0, Math.max(size, 1));

            long calls = countInvocations(inboxRepository)
                    + countInvocations(reviewHistoryRepository)
                    + countInvocations(userRepository);

            assertThat(calls)
                    .as("a service that asked per dish would make %d calls for a page of %d",
                            size, size)
                    .isLessThanOrEqualTo(4);
        }

        @Test
        @DisplayName("the history and author lookups are batched, called once each per page")
        void lookupsAreBatched() {
            List<Dish> dishes = new java.util.ArrayList<>();
            for (int i = 0; i < 20; i++) {
                dishes.add(dish("Plato " + i, DishStatus.PENDING_PRICE,
                        Instant.now().plusSeconds(i), UUID.randomUUID()));
            }
            when(inboxRepository.findPendingInbox(eq(TENANT), any()))
                    .thenReturn(pageOf(dishes, PageRequest.of(0, 20), 20));

            service.getInbox(InboxTab.PENDING, 0, 20);

            Mockito.verify(reviewHistoryRepository, Mockito.times(1))
                    .countRejectionsByDishIds(eq(TENANT), anyList());
            Mockito.verify(userRepository, Mockito.times(1)).findAllById(any());
            Mockito.verify(reviewHistoryRepository, Mockito.never())
                    .countByDishIdAndTenantId(any(), any());
        }

        private static long countInvocations(Object mock) {
            return mockingDetails(mock).getInvocations().stream()
                    .filter(i -> !i.getMethod().getName().equals("toString"))
                    .count();
        }
    }

    @Nested
    @DisplayName("empty and out-of-range pages (FR-005, FR-036)")
    class EmptyStates {

        @ParameterizedTest
        @EnumSource(InboxTab.class)
        @DisplayName("a view with no matches returns an empty list with accurate metadata")
        void emptyViewIsNotAnError(InboxTab tab) {
            DishReviewInboxPageResponse response = service.getInbox(tab, 0, 20);

            assertThat(response.items()).isEmpty();
            assertThat(response.totalElements()).isZero();
            assertThat(response.totalPages()).isZero();
            assertThat(response.hasNext()).isFalse();
            assertThat(response.page()).isZero();
            assertThat(response.size()).isEqualTo(20);
        }

        @Test
        @DisplayName("a page past the last one is empty with accurate metadata, not an error")
        void pastTheLastPage() {
            when(inboxRepository.findPendingInbox(eq(TENANT), any()))
                    .thenReturn(pageOf(List.of(), PageRequest.of(9, 20), 37));

            DishReviewInboxPageResponse response = service.getInbox(InboxTab.PENDING, 9, 20);

            assertThat(response.items()).isEmpty();
            assertThat(response.totalElements())
                    .as("FR-036: the metadata stays accurate so the UI can say where the "
                            + "Administrator actually is")
                    .isEqualTo(37L);
            assertThat(response.totalPages()).isEqualTo(2);
            assertThat(response.hasNext()).isFalse();
        }

        @Test
        @DisplayName("batched lookups are skipped entirely when the page is empty")
        void noLookupsForAnEmptyPage() {
            service.getInbox(InboxTab.PENDING, 0, 20);

            Mockito.verify(reviewHistoryRepository, Mockito.never())
                    .countRejectionsByDishIds(any(), anyList());
            Mockito.verify(userRepository, Mockito.never()).findAllById(any());
        }
    }

    @Nested
    @DisplayName("paging defaults and guards (FR-003)")
    class Paging {

        @Test
        @DisplayName("the default page size is 20")
        void defaultPageSize() {
            assertThat(service.getInbox(InboxTab.PENDING, 0, 20).size()).isEqualTo(20);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -20})
        @DisplayName("a non-positive page size is rejected rather than silently corrected")
        void nonPositiveSizeRejected(int size) {
            assertThat(catchThrowable(() -> service.getInbox(InboxTab.PENDING, 0, size)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a negative page index is rejected")
        void negativePageRejected() {
            assertThat(catchThrowable(() -> service.getInbox(InboxTab.PENDING, -1, 20)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a null tab is rejected: there is no default view to guess")
        void nullTabRejected() {
            assertThat(catchThrowable(() -> service.getInbox(null, 0, 20)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private static Throwable catchThrowable(org.assertj.core.api.ThrowableAssert
                .ThrowingCallable callable) {
            return org.assertj.core.api.Assertions.catchThrowable(callable);
        }
    }
}
