package com.easyeat.dish.controller;

import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.DishReviewInboxItemResponse;
import com.easyeat.dish.dto.DishReviewInboxPageResponse;
import com.easyeat.dish.dto.InboxTab;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.service.DishReviewDetailService;
import com.easyeat.dish.service.DishReviewInboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The inbox and detail endpoints of FR-001 through FR-006: role gate, tab validation, paging
 * defaults and the payload the contract publishes.
 *
 * <p>Pricing, approval and rejection belong to later stories and are added to this class there,
 * which is why it is created here rather than per story — three stories share the controller and
 * splitting it per story would create competing classes.
 */
@WebMvcTest(AdminDishReviewController.class)
@DisplayName("AdminDishReviewController: inbox and detail")
class AdminDishReviewControllerTest extends DishControllerTestBase {

    private static final String INBOX = "/api/v1/admin/dishes/review-inbox";

    @MockBean
    private DishReviewInboxService inboxService;

    @MockBean
    private DishReviewDetailService detailService;

    private static DishReviewInboxPageResponse emptyPage() {
        return new DishReviewInboxPageResponse(List.of(), 0, 20, 0L, 0, false, 0L);
    }

    private static DishReviewInboxItemResponse item(String name) {
        return new DishReviewInboxItemResponse(
                UUID.randomUUID(), name, DishCategory.PLATOS_PRINCIPALES,
                DishStatus.PENDING_PRICE, "Camila Duarte", false,
                Instant.parse("2026-10-01T10:00:00Z"), null, null, false, 0);
    }

    @Nested
    @DisplayName("the role gate (FR-001)")
    class RoleGate {

        @ParameterizedTest
        @ValueSource(strings = {"ADMIN", "ADMINISTRADOR"})
        @DisplayName("both the role and its alias are admitted")
        void adminRolesAdmitted(String role) throws Exception {
            when(inboxService.getInbox(any(), anyInt(), anyInt())).thenReturn(emptyPage());

            mockMvc.perform(get(INBOX).with(req -> {
                        req.setUserPrincipal(() -> "admin");
                        return req;
                    }).param("tab", "PENDING")
                    .with(org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.user("admin").roles(role)))
                    .andExpect(status().isOk());
        }

        @ParameterizedTest
        @ValueSource(strings = {"CHEF", "ENCARGADO", "COMENSAL"})
        @WithMockUser
        @DisplayName("every other role is refused with 403, and the list is exhaustive (FR-001)")
        void otherRolesRefused(String role) throws Exception {
            mockMvc.perform(get(INBOX).param("tab", "PENDING")
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors.user("x").roles(role)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("an unauthenticated request is refused before any tab validation")
        void anonymousRefused() throws Exception {
            mockMvc.perform(get(INBOX).param("tab", "NOT_A_TAB"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("tab and paging parameters (FR-003, FR-036)")
    @WithMockUser(roles = "ADMIN")
    class Parameters {

        @ParameterizedTest
        @CsvSource({"PENDING", "PUBLISHED", "HISTORY"})
        @DisplayName("each published tab value reaches the service unchanged")
        void tabsAreForwarded(String tab) throws Exception {
            when(inboxService.getInbox(any(), anyInt(), anyInt())).thenReturn(emptyPage());

            mockMvc.perform(get(INBOX).param("tab", tab)).andExpect(status().isOk());

            verify(inboxService).getInbox(eq(InboxTab.valueOf(tab)), eq(0), eq(20));
        }

        @Test
        @DisplayName("omitting the tab defaults to PENDING, the view the badge counts")
        void tabDefaultsToPending() throws Exception {
            when(inboxService.getInbox(any(), anyInt(), anyInt())).thenReturn(emptyPage());

            mockMvc.perform(get(INBOX)).andExpect(status().isOk());

            verify(inboxService).getInbox(eq(InboxTab.PENDING), eq(0), eq(20));
        }

        @Test
        @DisplayName("an unknown tab is a 400, not a silent fallback to a default view")
        void unknownTabIsBadRequest() throws Exception {
            mockMvc.perform(get(INBOX).param("tab", "REJECTED_ONLY"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("the default page size is 20 (FR-003)")
        void defaultPageSizeIsTwenty() throws Exception {
            when(inboxService.getInbox(any(), anyInt(), anyInt())).thenReturn(emptyPage());

            mockMvc.perform(get(INBOX).param("tab", "PENDING")).andExpect(status().isOk());

            verify(inboxService).getInbox(any(), eq(0), eq(20));
        }

        @Test
        @DisplayName("explicit page and size are forwarded as given")
        void explicitPaging() throws Exception {
            when(inboxService.getInbox(any(), anyInt(), anyInt())).thenReturn(emptyPage());

            mockMvc.perform(get(INBOX).param("tab", "PENDING")
                            .param("page", "3").param("size", "50"))
                    .andExpect(status().isOk());

            verify(inboxService).getInbox(eq(InboxTab.PENDING), eq(3), eq(50));
        }

        @Test
        @DisplayName("a page past the last one is 200 with an empty list, never 404 (FR-036)")
        void pastTheLastPageIsOk() throws Exception {
            when(inboxService.getInbox(any(), anyInt(), anyInt()))
                    .thenReturn(new DishReviewInboxPageResponse(
                            List.of(), 9, 20, 37L, 2, false, 37L));

            mockMvc.perform(get(INBOX).param("tab", "PENDING").param("page", "9"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(37))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }
    }

    @Nested
    @DisplayName("the inbox payload the contract publishes (FR-004, FR-005)")
    @WithMockUser(roles = "ADMIN")
    class InboxPayload {

        @Test
        @DisplayName("every field FR-004 names is present on an item")
        void itemCarriesEveryNamedField() throws Exception {
            when(inboxService.getInbox(any(), anyInt(), anyInt()))
                    .thenReturn(new DishReviewInboxPageResponse(
                            List.of(item("Risotto de Hongos")), 0, 20, 1L, 1, false, 1L));

            mockMvc.perform(get(INBOX).param("tab", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].dishId").exists())
                    .andExpect(jsonPath("$.items[0].name").value("Risotto de Hongos"))
                    .andExpect(jsonPath("$.items[0].category").exists())
                    .andExpect(jsonPath("$.items[0].authorName").value("Camila Duarte"))
                    .andExpect(jsonPath("$.items[0].submittedAt").exists())
                    .andExpect(jsonPath("$.items[0].status").value("PENDING_PRICE"))
                    .andExpect(jsonPath("$.items[0].isResubmission").value(false))
                    .andExpect(jsonPath("$.items[0].reviewCycleCount").value(0))
                    .andExpect(jsonPath("$.items[0].authorDeactivated").value(false));
        }

        @Test
        @DisplayName("the pending badge rides on every response, whatever the tab (FR-003)")
        void pendingCountAlwaysPresent() throws Exception {
            when(inboxService.getInbox(any(), anyInt(), anyInt()))
                    .thenReturn(new DishReviewInboxPageResponse(
                            List.of(), 0, 20, 0L, 0, false, 37L));

            mockMvc.perform(get(INBOX).param("tab", "PUBLISHED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pendingCount").value(37));
        }

        @Test
        @DisplayName("an empty view is 200 with an empty list, not 404 (FR-005)")
        void emptyViewIsOk() throws Exception {
            when(inboxService.getInbox(any(), anyInt(), anyInt())).thenReturn(emptyPage());

            mockMvc.perform(get(INBOX).param("tab", "HISTORY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.pendingCount").value(0));
        }

        @Test
        @DisplayName("an absent author serialises as a present null, not as an omitted key")
        void nullAuthorIsPresentAsNull() throws Exception {
            DishReviewInboxItemResponse anonymous = new DishReviewInboxItemResponse(
                    UUID.randomUUID(), "Tarta", DishCategory.POSTRES, DishStatus.PENDING_PRICE,
                    null, false, Instant.now(), null, null, false, 0);
            when(inboxService.getInbox(any(), anyInt(), anyInt()))
                    .thenReturn(new DishReviewInboxPageResponse(
                            List.of(anonymous), 0, 20, 1L, 1, false, 1L));

            mockMvc.perform(get(INBOX).param("tab", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].authorName").value((Object) null));
        }
    }

    @Nested
    @DisplayName("the detail endpoint (FR-006)")
    @WithMockUser(roles = "ADMIN")
    class Detail {

        @Test
        @DisplayName("an unknown dish is 404, with the RFC 7807 shape the project uses")
        void unknownDishIsNotFound() throws Exception {
            UUID missing = UUID.randomUUID();
            when(detailService.getDetail(missing)).thenThrow(new DishNotFoundException(missing));

            mockMvc.perform(get("/api/v1/admin/dishes/" + missing + "/review-detail"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type")
                            .value("https://easyeat.com/errors/dish-not-found"));
        }

        @Test
        @DisplayName("a malformed identifier is a 400, not a 404")
        void malformedIdIsBadRequest() throws Exception {
            mockMvc.perform(get("/api/v1/admin/dishes/not-a-uuid/review-detail"))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest
        @ValueSource(strings = {"CHEF", "COMENSAL"})
        @DisplayName("the detail endpoint carries the same role gate as the inbox")
        void detailIsRoleGated(String role) throws Exception {
            mockMvc.perform(get("/api/v1/admin/dishes/" + UUID.randomUUID() + "/review-detail")
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors.user("x").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }
}
