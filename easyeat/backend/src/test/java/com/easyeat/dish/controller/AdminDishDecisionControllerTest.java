package com.easyeat.dish.controller;

import com.easyeat.dish.domain.AcknowledgementKind;
import com.easyeat.dish.domain.PublicationGateCondition;
import com.easyeat.dish.dto.DishApprovalResponse;
import com.easyeat.dish.dto.DishPricingResponse;
import com.easyeat.dish.dto.DishRejectionResponse;
import com.easyeat.dish.dto.MarginSummary;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.dish.exception.AcknowledgementRequiredException;
import com.easyeat.dish.exception.DishAlreadyDecidedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.DishStateConflictException;
import com.easyeat.dish.exception.InvalidRejectionCommentException;
import com.easyeat.dish.exception.InvalidRetailPriceException;
import com.easyeat.dish.exception.PublicationGateException;
import com.easyeat.dish.exception.StaleDishPriceException;
import com.easyeat.dish.service.DishGovernanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The pricing (FR-009, FR-013, FR-014, SC-003) and approval (FR-015 through FR-019, SC-003)
 * portions of the decision surface — {@code PUT /admin/dishes/{id}/retail-price} and {@code POST
 * /admin/dishes/{id}/approval}. Rejection is added to this class by US4, which is why the class is
 * created here rather than per story (same posture as {@code AdminDishReviewController}).
 *
 * <p>This is a web-slice test: the service is mocked, so the assertions are about role gating,
 * request shaping and the exact HTTP status/body the contract publishes for each exception
 * {@link DishGovernanceService} is documented to raise — not about the pricing or gate logic
 * itself, which belongs to {@code DishGovernanceServiceTest}.
 */
@WebMvcTest(AdminDishDecisionController.class)
@DisplayName("AdminDishDecisionController: pricing and approval")
class AdminDishDecisionControllerTest extends DishControllerTestBase {

    private static final String RETAIL_PRICE = "/api/v1/admin/dishes/%s/retail-price";

    @MockBean
    private DishGovernanceService governanceService;

    private final UUID dishId = UUID.randomUUID();

    private static MarginSummary margin() {
        return new MarginSummary(new BigDecimal("4500.00"), new BigDecimal("11250.00"),
                new BigDecimal("60.0000"), new BigDecimal("0.0000"), new BigDecimal("20.00"),
                false, true);
    }

    private static DishPricingResponse pricingResponse(DishStatus status) {
        return new DishPricingResponse(UUID.randomUUID(), status, margin(), UUID.randomUUID());
    }

    private String url() {
        return String.format(RETAIL_PRICE, dishId);
    }

    @Nested
    @DisplayName("the role gate (FR-001)")
    class RoleGate {

        @ParameterizedTest
        @ValueSource(strings = {"ADMIN", "ADMINISTRADOR"})
        @DisplayName("both the role and its alias are admitted")
        void adminRolesAdmitted(String role) throws Exception {
            when(governanceService.setRetailPrice(any(), any(), any(), anyBoolean()))
                    .thenReturn(pricingResponse(DishStatus.PENDING_PRICE));

            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 11250.00, "expectedRetailPrice": null}
                                    """)
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors.user("admin").roles(role)))
                    .andExpect(status().isOk());
        }

        @ParameterizedTest
        @ValueSource(strings = {"CHEF", "ENCARGADO", "COMENSAL"})
        @DisplayName("every other role is refused with 403, and the list is exhaustive (FR-001)")
        void otherRolesRefused(String role) throws Exception {
            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 11250.00, "expectedRetailPrice": null}
                                    """)
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors.user("x").roles(role)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("an unauthenticated request is refused")
        void anonymousRefused() throws Exception {
            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 11250.00, "expectedRetailPrice": null}
                                    """))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @WithMockUser(roles = "ADMIN")
    @DisplayName("price validation (FR-013)")
    class PriceValidation {

        @Test
        @DisplayName("a price of exactly 0.00 is rejected as invalid, not stale (400)")
        void zeroPriceRejected() throws Exception {
            when(governanceService.setRetailPrice(eq(dishId), any(), any(), anyBoolean()))
                    .thenThrow(new InvalidRetailPriceException(new BigDecimal("0.00")));

            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 0.00, "expectedRetailPrice": null}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/invalid-retail-price"))
                    .andExpect(jsonPath("$.errors[0].field").value("retailPrice"));
        }

        @Test
        @DisplayName("a negative price is rejected (400)")
        void negativePriceRejected() throws Exception {
            when(governanceService.setRetailPrice(eq(dishId), any(), any(), anyBoolean()))
                    .thenThrow(new InvalidRetailPriceException(new BigDecimal("-5.00")));

            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": -5.00, "expectedRetailPrice": null}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/invalid-retail-price"));
        }

        @Test
        @DisplayName("a price with more than two decimals is rejected (400)")
        void threeDecimalsRejected() throws Exception {
            when(governanceService.setRetailPrice(eq(dishId), any(), any(), anyBoolean()))
                    .thenThrow(new InvalidRetailPriceException(new BigDecimal("11250.123")));

            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 11250.123, "expectedRetailPrice": null}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/invalid-retail-price"));
        }

        @Test
        @DisplayName("expectedRetailPrice absent from the body is a 400 field error, never a silent overwrite")
        void expectedRetailPriceAbsentRejected() throws Exception {
            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 11250.00}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/expected-retail-price-required"))
                    .andExpect(jsonPath("$.errors[0].field").value("expectedRetailPrice"));
        }

        @Test
        @DisplayName("an explicit expectedRetailPrice: null is accepted as the first-pricing assertion")
        void explicitNullExpectedPriceAccepted() throws Exception {
            when(governanceService.setRetailPrice(eq(dishId), eq(new BigDecimal("11250.00")),
                    isNull(), eq(false)))
                    .thenReturn(pricingResponse(DishStatus.PENDING_PRICE));

            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 11250.00, "expectedRetailPrice": null}
                                    """))
                    .andExpect(status().isOk());

            verify(governanceService).setRetailPrice(eq(dishId), eq(new BigDecimal("11250.00")),
                    isNull(), eq(false));
        }
    }

    @Nested
    @WithMockUser(roles = "ADMIN")
    @DisplayName("concurrency (FR-025)")
    class Concurrency {

        @Test
        @DisplayName("a mismatched expectedRetailPrice yields 409 dish-stale-price with the prevailing price")
        void mismatchedExpectedPriceYieldsStalePrice() throws Exception {
            when(governanceService.setRetailPrice(eq(dishId), any(), eq(new BigDecimal("10800.00")), anyBoolean()))
                    .thenThrow(new StaleDishPriceException(dishId, new BigDecimal("10800.00"),
                            new BigDecimal("11250.00")));

            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 12000.00, "expectedRetailPrice": 10800.00}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/dish-stale-price"))
                    .andExpect(jsonPath("$.currentRetailPrice").value(11250.00));
        }

        @Test
        @DisplayName("repricing a dish in DRAFT is rejected with 409 dish-invalid-state")
        void repricingDraftRejected() throws Exception {
            when(governanceService.setRetailPrice(eq(dishId), any(), any(), anyBoolean()))
                    .thenThrow(new DishStateConflictException(dishId, "DRAFT",
                            "PENDING_PRICE or PUBLISHED"));

            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 11250.00, "expectedRetailPrice": null}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/dish-invalid-state"));
        }

        @Test
        @DisplayName("repricing a PUBLISHED dish succeeds and reports its status unchanged")
        void repricingPublishedDishSucceeds() throws Exception {
            when(governanceService.setRetailPrice(eq(dishId), any(), any(), anyBoolean()))
                    .thenReturn(pricingResponse(DishStatus.PUBLISHED));

            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 13000.00, "expectedRetailPrice": 11250.00}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PUBLISHED"));
        }
    }

    @Nested
    @WithMockUser(roles = "ADMIN")
    @DisplayName("the negative-margin confirmation (FR-014)")
    class NegativeMarginConfirmation {

        @Test
        @DisplayName("a price below the Food Cost without acknowledgement yields 422 acknowledgement-required")
        void negativeMarginWithoutAcknowledgementBlocked() throws Exception {
            when(governanceService.setRetailPrice(eq(dishId), any(), any(), eq(false)))
                    .thenThrow(new AcknowledgementRequiredException(dishId,
                            List.of(AcknowledgementKind.NEGATIVE_MARGIN)));

            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 100.00, "expectedRetailPrice": null}
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/acknowledgement-required"))
                    .andExpect(jsonPath("$.requiredAcknowledgements[0]").value("NEGATIVE_MARGIN"));
        }

        @Test
        @DisplayName("acknowledging the negative margin lets the save proceed")
        void negativeMarginAcknowledgedSucceeds() throws Exception {
            when(governanceService.setRetailPrice(eq(dishId), any(), any(), eq(true)))
                    .thenReturn(pricingResponse(DishStatus.PENDING_PRICE));

            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 100.00, "expectedRetailPrice": null, \
                                    "acknowledgeNegativeMargin": true}
                                    """))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @WithMockUser(roles = "ADMIN")
    @DisplayName("lookup failures")
    class LookupFailures {

        @Test
        @DisplayName("a dish that does not exist anywhere is a 404")
        void dishNotFoundYields404() throws Exception {
            when(governanceService.setRetailPrice(eq(dishId), any(), any(), anyBoolean()))
                    .thenThrow(new DishNotFoundException(dishId));

            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 11250.00, "expectedRetailPrice": null}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("a dish belonging to another establishment is a 403, not a 404 (FR-001, "
                + "SC-003) — spec.md's Edge Cases rule out 404 as an alternative here")
        void crossTenantDishYields403() throws Exception {
            when(governanceService.setRetailPrice(eq(dishId), any(), any(), anyBoolean()))
                    .thenThrow(new TenantViolationException(UUID.randomUUID(), UUID.randomUUID()));

            mockMvc.perform(put(url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"retailPrice": 11250.00, "expectedRetailPrice": null}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    private static final String APPROVAL = "/api/v1/admin/dishes/%s/approval";

    private String approvalUrl() {
        return String.format(APPROVAL, dishId);
    }

    private static DishApprovalResponse approvalResponse(boolean alreadyPublished) {
        return new DishApprovalResponse(UUID.randomUUID(), DishStatus.PUBLISHED, alreadyPublished,
                Instant.parse("2026-10-30T10:00:00Z"),
                alreadyPublished ? null : UUID.randomUUID());
    }

    @Nested
    @DisplayName("the role gate on approval (FR-001)")
    class ApprovalRoleGate {

        @Test
        @WithMockUser(roles = "ADMINISTRADOR")
        @DisplayName("the ADMINISTRADOR alias is admitted")
        void administradorAdmitted() throws Exception {
            when(governanceService.approve(eq(dishId), anyBoolean(), anyBoolean()))
                    .thenReturn(approvalResponse(false));

            mockMvc.perform(post(approvalUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());
        }

        @ParameterizedTest
        @ValueSource(strings = {"CHEF", "ENCARGADO", "COMENSAL"})
        @DisplayName("every other role is refused with 403")
        void otherRolesRefused(String role) throws Exception {
            mockMvc.perform(post(approvalUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors.user("x").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @WithMockUser(roles = "ADMIN")
    @DisplayName("the publication gate (FR-015, FR-018, FR-019)")
    class ApprovalGate {

        @Test
        @DisplayName("every gate failure carries its own unmetConditions entry")
        void gateFailureNamesUnmetConditions() throws Exception {
            when(governanceService.approve(eq(dishId), anyBoolean(), anyBoolean()))
                    .thenThrow(new PublicationGateException(dishId,
                            List.of(PublicationGateCondition.RETAIL_PRICE_MISSING,
                                    PublicationGateCondition.EMPTY_RECIPE)));

            mockMvc.perform(post(approvalUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/publication-gate-failed"))
                    .andExpect(jsonPath("$.unmetConditions", org.hamcrest.Matchers.hasSize(2)))
                    .andExpect(jsonPath("$.unmetConditions[0]").value("RETAIL_PRICE_MISSING"))
                    .andExpect(jsonPath("$.unmetConditions[1]").value("EMPTY_RECIPE"));
        }

        @Test
        @DisplayName("approving a dish in DRAFT is a 409 dish-invalid-state")
        void draftYields409() throws Exception {
            when(governanceService.approve(eq(dishId), anyBoolean(), anyBoolean()))
                    .thenThrow(new DishStateConflictException(dishId, "DRAFT", "PENDING_PRICE"));

            mockMvc.perform(post(approvalUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/dish-invalid-state"));
        }

        @Test
        @DisplayName("approving an already-PUBLISHED dish is an idempotent 200, not a conflict")
        void alreadyPublishedIsIdempotent() throws Exception {
            when(governanceService.approve(eq(dishId), anyBoolean(), anyBoolean()))
                    .thenReturn(approvalResponse(true));

            mockMvc.perform(post(approvalUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alreadyPublished").value(true))
                    .andExpect(jsonPath("$.reviewHistoryEntryId").doesNotExist());
        }

        @Test
        @DisplayName("another administrator deciding first yields 409 dish-already-decided")
        void raceYields409() throws Exception {
            when(governanceService.approve(eq(dishId), anyBoolean(), anyBoolean()))
                    .thenThrow(new DishAlreadyDecidedException(dishId, "DRAFT", Instant.now()));

            mockMvc.perform(post(approvalUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/dish-already-decided"));
        }

        @Test
        @DisplayName("the acknowledgement challenge for unpriced ingredients or a negative margin")
        void acknowledgementChallenge() throws Exception {
            when(governanceService.approve(eq(dishId), eq(false), eq(false)))
                    .thenThrow(new AcknowledgementRequiredException(dishId,
                            List.of(AcknowledgementKind.UNPRICED_INGREDIENTS,
                                    AcknowledgementKind.NEGATIVE_MARGIN)));

            mockMvc.perform(post(approvalUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/acknowledgement-required"))
                    .andExpect(jsonPath("$.requiredAcknowledgements", org.hamcrest.Matchers.hasSize(2)));
        }

        @Test
        @DisplayName("both acknowledgement flags are forwarded to the service")
        void acknowledgementFlagsForwarded() throws Exception {
            when(governanceService.approve(eq(dishId), eq(true), eq(true)))
                    .thenReturn(approvalResponse(false));

            mockMvc.perform(post(approvalUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"acknowledgeUnpricedIngredients": true, "acknowledgeNegativeMargin": true}
                                    """))
                    .andExpect(status().isOk());

            verify(governanceService).approve(eq(dishId), eq(true), eq(true));
        }

        @Test
        @DisplayName("an empty body defaults both acknowledgement flags to false")
        void emptyBodyDefaultsToFalse() throws Exception {
            when(governanceService.approve(eq(dishId), eq(false), eq(false)))
                    .thenReturn(approvalResponse(false));

            mockMvc.perform(post(approvalUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());

            verify(governanceService).approve(eq(dishId), eq(false), eq(false));
        }

        @Test
        @DisplayName("a dish that does not exist anywhere is a 404")
        void dishNotFoundYields404() throws Exception {
            when(governanceService.approve(eq(dishId), anyBoolean(), anyBoolean()))
                    .thenThrow(new DishNotFoundException(dishId));

            mockMvc.perform(post(approvalUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("a dish belonging to another establishment is a 403, not a 404 (FR-001, "
                + "SC-003) — spec.md's Edge Cases rule out 404 as an alternative here")
        void crossTenantDishYields403() throws Exception {
            when(governanceService.approve(eq(dishId), anyBoolean(), anyBoolean()))
                    .thenThrow(new TenantViolationException(UUID.randomUUID(), UUID.randomUUID()));

            mockMvc.perform(post(approvalUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    private static final String REJECTION = "/api/v1/admin/dishes/%s/rejection";

    private String rejectionUrl() {
        return String.format(REJECTION, dishId);
    }

    private static final String VALID_COMMENT =
            "El costo del salmón dispara el food cost. Reemplazalo por merluza y reenviá la ficha.";

    private static String rejectionBody(String comment) {
        return "{\"comment\": " + (comment == null ? "null" : "\"" + comment + "\"") + "}";
    }

    private static DishRejectionResponse rejectionResponse() {
        return new DishRejectionResponse(UUID.randomUUID(), DishStatus.DRAFT, 1, UUID.randomUUID());
    }

    @Nested
    @WithMockUser(roles = "ADMIN")
    @DisplayName("rejection: feedback to the kitchen (FR-020, FR-021, FR-024)")
    class Rejection {

        @Test
        @DisplayName("a valid comment rejects the dish and returns the new cycle")
        void validRejectionSucceeds() throws Exception {
            when(governanceService.reject(eq(dishId), eq(VALID_COMMENT)))
                    .thenReturn(rejectionResponse());

            mockMvc.perform(post(rejectionUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rejectionBody(VALID_COMMENT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "123456789",
                "          ",
        })
        @DisplayName("a comment at 9 characters or whitespace-only is a 400")
        void shortOrBlankCommentRejected(String comment) throws Exception {
            when(governanceService.reject(eq(dishId), eq(comment)))
                    .thenThrow(new InvalidRejectionCommentException(comment));

            mockMvc.perform(post(rejectionUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rejectionBody(comment)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/invalid-rejection-comment"))
                    .andExpect(jsonPath("$.errors[0].field").value("comment"));
        }

        @Test
        @DisplayName("a comment at exactly 10 characters is accepted")
        void tenCharacterCommentAccepted() throws Exception {
            String tenChars = "1234567890";
            when(governanceService.reject(eq(dishId), eq(tenChars)))
                    .thenReturn(rejectionResponse());

            mockMvc.perform(post(rejectionUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rejectionBody(tenChars)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a comment at exactly 1000 characters is accepted")
        void thousandCharacterCommentAccepted() throws Exception {
            String thousandChars = "a".repeat(1000);
            when(governanceService.reject(eq(dishId), eq(thousandChars)))
                    .thenReturn(rejectionResponse());

            mockMvc.perform(post(rejectionUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rejectionBody(thousandChars)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a comment at 1001 characters is a 400")
        void tooLongCommentRejected() throws Exception {
            String tooLong = "a".repeat(1001);
            when(governanceService.reject(eq(dishId), eq(tooLong)))
                    .thenThrow(new InvalidRejectionCommentException(tooLong));

            mockMvc.perform(post(rejectionUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rejectionBody(tooLong)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rejecting a PUBLISHED dish is a 409")
        void publishedDishYields409() throws Exception {
            when(governanceService.reject(eq(dishId), eq(VALID_COMMENT)))
                    .thenThrow(new DishStateConflictException(dishId, "PUBLISHED", "PENDING_PRICE"));

            mockMvc.perform(post(rejectionUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rejectionBody(VALID_COMMENT)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("markup in the comment is forwarded verbatim, never stripped at this layer")
        void markupStoredVerbatim() throws Exception {
            String withMarkup = "<b>urgente</b> revisar el costo del salmón antes de reenviar";
            when(governanceService.reject(eq(dishId), eq(withMarkup)))
                    .thenReturn(rejectionResponse());

            mockMvc.perform(post(rejectionUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rejectionBody(withMarkup)))
                    .andExpect(status().isOk());

            verify(governanceService).reject(eq(dishId), eq(withMarkup));
        }
    }
}
