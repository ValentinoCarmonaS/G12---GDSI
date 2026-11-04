package com.easyeat.dish.controller;

import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.dish.dto.ChefReviewFeedbackEntry;
import com.easyeat.dish.dto.ChefReviewFeedbackResponse;
import com.easyeat.dish.service.ChefReviewFeedbackService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /dishes/{id}/review-feedback} (FR-023, SC-003): the Chef's financially-blind read of
 * their own establishment's rejection feedback.
 *
 * <p>The structural absence of any financial field is asserted against the raw JSON body, not
 * only against the DTO's declared fields — a field the serializer adds through some other path
 * (an interceptor, a mixin, a parent class) would still show up there, which asserting against
 * {@link ChefReviewFeedbackResponse}'s own getters could not catch.
 */
@WebMvcTest(ChefDishReviewFeedbackController.class)
@DisplayName("ChefDishReviewFeedbackController (FR-023)")
class ChefDishReviewFeedbackControllerTest extends DishControllerTestBase {

    /**
     * Matches a JSON <em>key</em> whose name contains a financial term, not the term appearing
     * anywhere in the body — the Chef's own feedback comment is free text an Administrator wrote
     * and may legitimately say "el costo del salmón..."; FR-023 is about no financial *field*
     * existing on the payload, not about censoring prose that happens to use the word.
     */
    private static final Pattern FINANCIAL_KEY =
            Pattern.compile("\"[a-zA-Z]*(cost|margin|price)[a-zA-Z]*\"\\s*:", Pattern.CASE_INSENSITIVE);

    @MockBean
    private ChefReviewFeedbackService feedbackService;

    private final UUID dishId = UUID.randomUUID();

    private String url() {
        return "/api/v1/dishes/" + dishId + "/review-feedback";
    }

    private static ChefReviewFeedbackResponse feedback() {
        return new ChefReviewFeedbackResponse(UUID.randomUUID(), com.easyeat.dish.domain.DishStatus.DRAFT, List.of(
                new ChefReviewFeedbackEntry(2, "Lucía Ferraro",
                        "El costo del salmón dispara el food cost. Usá merluza.",
                        new com.easyeat.dish.dto.DishProposedChanges("Milanesa de ternera",
                                "Cambia la carne por ternera.", List.of("GLUTEN"), null, null),
                        Instant.parse("2026-10-29T10:00:00Z")),
                new ChefReviewFeedbackEntry(1, "Lucía Ferraro",
                        "Faltan las fotos del plato.",
                        null,
                        Instant.parse("2026-10-20T10:00:00Z"))));
    }

    @Nested
    @DisplayName("the role gate (FR-001, FR-023)")
    class RoleGate {

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("the Chef of the establishment can read the feedback")
        void chefAllowed() throws Exception {
            when(feedbackService.getFeedback(dishId)).thenReturn(feedback());

            mockMvc.perform(get(url())).andExpect(status().isOk());
        }

        @ParameterizedTest
        @ValueSource(strings = {"ADMIN", "ADMINISTRADOR", "ENCARGADO", "COMENSAL"})
        @DisplayName("every other role is refused with 403")
        void otherRolesRefused(String role) throws Exception {
            mockMvc.perform(get(url())
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors.user("x").roles(role)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("an unauthenticated request is refused")
        void anonymousRefused() throws Exception {
            mockMvc.perform(get(url())).andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @WithMockUser(roles = "CHEF")
    @DisplayName("tenant isolation (FR-001, SC-003)")
    class TenantIsolation {

        @Test
        @DisplayName("a dish of another establishment is forbidden, not merely absent")
        void crossTenantForbidden() throws Exception {
            when(feedbackService.getFeedback(dishId))
                    .thenThrow(new TenantViolationException(UUID.randomUUID(), UUID.randomUUID()));

            mockMvc.perform(get(url())).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("a dish that does not exist anywhere is a 404")
        void unknownDishNotFound() throws Exception {
            when(feedbackService.getFeedback(dishId))
                    .thenThrow(new DishNotFoundException(dishId));

            mockMvc.perform(get(url())).andExpect(status().isNotFound());
        }
    }

    @Nested
    @WithMockUser(roles = "CHEF")
    @DisplayName("financial confidentiality (FR-023, SC-003)")
    class FinancialConfidentiality {

        @Test
        @DisplayName("the response carries only text, timestamp and reviewer identity")
        void payloadShapeIsBlind() throws Exception {
            when(feedbackService.getFeedback(dishId)).thenReturn(feedback());

            mockMvc.perform(get(url()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.entries[0].cycleNumber").value(2))
                    .andExpect(jsonPath("$.entries[0].reviewerName").value("Lucía Ferraro"))
                    .andExpect(jsonPath("$.entries[0].comment").exists())
                    .andExpect(jsonPath("$.entries[0].proposedChanges").exists())
                    .andExpect(jsonPath("$.entries[0].proposedChanges.title").value("Milanesa de ternera"))
                    .andExpect(jsonPath("$.entries[0].createdAt").exists());
        }

        @Test
        @DisplayName("no cost, margin or price key exists anywhere in the raw response body")
        void noFinancialKeyInRawBody() throws Exception {
            when(feedbackService.getFeedback(dishId)).thenReturn(feedback());

            String body = mockMvc.perform(get(url()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(FINANCIAL_KEY.matcher(body).find()).isFalse();
        }
    }
}
