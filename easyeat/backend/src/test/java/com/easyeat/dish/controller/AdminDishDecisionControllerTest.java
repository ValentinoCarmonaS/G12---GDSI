package com.easyeat.dish.controller;

import com.easyeat.dish.domain.AcknowledgementKind;
import com.easyeat.dish.dto.DishPricingResponse;
import com.easyeat.dish.dto.MarginSummary;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.exception.AcknowledgementRequiredException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.DishStateConflictException;
import com.easyeat.dish.exception.InvalidRetailPriceException;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The pricing portion of the decision surface (FR-009, FR-013, FR-014, SC-003) — {@code PUT
 * /admin/dishes/{id}/retail-price}. Approval and rejection are added to this class by US3 and
 * US4, which is why the class is created here rather than per story (same posture as
 * {@code AdminDishReviewController}).
 *
 * <p>This is a web-slice test: the service is mocked, so the assertions are about role gating,
 * request shaping and the exact HTTP status/body the contract publishes for each exception
 * {@link DishGovernanceService} is documented to raise — not about the pricing arithmetic itself,
 * which belongs to {@code DishGovernanceServiceTest}.
 */
@WebMvcTest(AdminDishDecisionController.class)
@DisplayName("AdminDishDecisionController: retail pricing (FR-009, FR-013, FR-014)")
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
        @DisplayName("a dish not found in the caller's establishment is a 404, covering cross-tenant access too")
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
    }
}
