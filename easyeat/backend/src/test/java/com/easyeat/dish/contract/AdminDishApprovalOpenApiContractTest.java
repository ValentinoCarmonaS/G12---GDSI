package com.easyeat.dish.contract;

import com.easyeat.dish.controller.AdminDishDecisionController;
import com.easyeat.dish.controller.AdminDishReviewController;
import com.easyeat.dish.controller.ChefDishReviewFeedbackController;
import com.easyeat.dish.controller.DishControllerTestBase;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.ChefReviewFeedbackResponse;
import com.easyeat.dish.dto.DishApprovalResponse;
import com.easyeat.dish.dto.DishAuditHistoryResponse;
import com.easyeat.dish.dto.DishPricingResponse;
import com.easyeat.dish.dto.DishRejectionResponse;
import com.easyeat.dish.dto.DishReviewDetailResponse;
import com.easyeat.dish.dto.DishReviewInboxPageResponse;
import com.easyeat.dish.dto.MarginSummary;
import com.easyeat.dish.service.ChefReviewFeedbackService;
import com.easyeat.dish.service.DishGovernanceService;
import com.easyeat.dish.service.DishReviewDetailService;
import com.easyeat.dish.service.DishReviewInboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T102 (Phase 7): asserts the seven operations {@code contracts/openapi.yaml} publishes for this
 * feature all exist in the file and that each has a representative implementation whose
 * status/shape/content-type matches what the contract describes — mirroring {@code
 * auth/contract/OpenApiContractTest}'s level of rigor (path strings plus one request/response
 * shape check per operation), not a full JSON-schema validation.
 */
@WebMvcTest({AdminDishReviewController.class, AdminDishDecisionController.class,
        ChefDishReviewFeedbackController.class})
@DisplayName("AdminDishApprovalOpenApiContractTest (T102 - Spec 007 OpenAPI Contract)")
class AdminDishApprovalOpenApiContractTest extends DishControllerTestBase {

    @MockBean
    private DishReviewInboxService inboxService;

    @MockBean
    private DishReviewDetailService detailService;

    @MockBean
    private DishGovernanceService governanceService;

    @MockBean
    private ChefReviewFeedbackService chefReviewFeedbackService;

    private final UUID dishId = UUID.randomUUID();

    private static MarginSummary margin() {
        return new MarginSummary(new BigDecimal("4500.00"), new BigDecimal("11250.00"),
                new BigDecimal("60.0000"), new BigDecimal("0.0000"), new BigDecimal("20.00"),
                false, true);
    }

    @Test
    @DisplayName("The OpenAPI contract file exists and defines all seven of this feature's operations")
    void shouldVerifyOpenApiContractFileDefinesAllSevenOperations() throws Exception {
        Path contractPath = Paths.get("../../specs/007-admin-dish-approval-pricing/contracts/openapi.yaml")
                .toAbsolutePath().normalize();
        if (!Files.exists(contractPath)) {
            contractPath = Paths.get("specs/007-admin-dish-approval-pricing/contracts/openapi.yaml")
                    .toAbsolutePath().normalize();
        }

        assertThat(Files.exists(contractPath))
                .withFailMessage("OpenAPI contract file not found at " + contractPath)
                .isTrue();

        String contractYaml = Files.readString(contractPath);
        assertThat(contractYaml).contains("openapi: 3.0.3");
        assertThat(contractYaml).contains("/admin/dishes/review-inbox:");
        assertThat(contractYaml).contains("/admin/dishes/{id}/review-detail:");
        assertThat(contractYaml).contains("/admin/dishes/{id}/retail-price:");
        assertThat(contractYaml).contains("/admin/dishes/{id}/approval:");
        assertThat(contractYaml).contains("/admin/dishes/{id}/rejection:");
        assertThat(contractYaml).contains("/admin/dishes/{id}/review-history:");
        assertThat(contractYaml).contains("/dishes/{id}/review-feedback:");
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("GET /admin/dishes/review-inbox matches the contract's 200 page shape")
    void shouldConformToReviewInboxContract() throws Exception {
        when(inboxService.getInbox(any(), anyInt(), anyInt())).thenReturn(
                new DishReviewInboxPageResponse(List.of(), 0, 20, 0, 0, false, 0));

        mockMvc.perform(get("/api/v1/admin/dishes/review-inbox"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("GET /admin/dishes/{id}/review-detail matches the contract's 200 detail shape")
    void shouldConformToReviewDetailContract() throws Exception {
        when(detailService.getDetail(dishId)).thenReturn(new DishReviewDetailResponse(
                dishId, "Milanesa", "Desc", null, DishStatus.PENDING_PRICE,
                new BigDecimal("5000.00"), null, "Chef Test", false, true,
                Instant.now(), null, 0, false, margin(), null, false, null));

        mockMvc.perform(get("/api/v1/admin/dishes/{id}/review-detail", dishId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dishId").value(dishId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_PRICE"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("PUT /admin/dishes/{id}/retail-price matches the contract's 200 pricing shape")
    void shouldConformToRetailPriceContract() throws Exception {
        when(governanceService.setRetailPrice(any(), any(), any(), anyBoolean())).thenReturn(
                new DishPricingResponse(dishId, DishStatus.PENDING_PRICE, margin(), UUID.randomUUID()));

        mockMvc.perform(put("/api/v1/admin/dishes/{id}/retail-price", dishId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retailPrice\":11250.00,\"expectedRetailPrice\":null,\"acknowledgeNegativeMargin\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dishId").value(dishId.toString()))
                .andExpect(jsonPath("$.margin.marginPct").value(60.0));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("POST /admin/dishes/{id}/approval matches the contract's 200 approval shape")
    void shouldConformToApprovalContract() throws Exception {
        when(governanceService.approve(any(), anyBoolean(), anyBoolean())).thenReturn(
                new DishApprovalResponse(dishId, DishStatus.PUBLISHED, false, Instant.now(), UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/admin/dishes/{id}/approval", dishId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acknowledgeUnpricedIngredients\":false,\"acknowledgeNegativeMargin\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("POST /admin/dishes/{id}/rejection matches the contract's 200 rejection shape")
    void shouldConformToRejectionContract() throws Exception {
        when(governanceService.reject(any(), any())).thenReturn(
                new DishRejectionResponse(dishId, DishStatus.DRAFT, 1, UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/admin/dishes/{id}/rejection", dishId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Falta especificar el tipo de salsa en la receta.\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.cycleNumber").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("GET /admin/dishes/{id}/review-history matches the contract's 200 history shape")
    void shouldConformToReviewHistoryContract() throws Exception {
        when(detailService.getHistory(dishId)).thenReturn(
                new DishAuditHistoryResponse(dishId, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/admin/dishes/{id}/review-history", dishId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dishId").value(dishId.toString()))
                .andExpect(jsonPath("$.reviewEvents").isArray())
                .andExpect(jsonPath("$.priceChanges").isArray());
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("GET /dishes/{id}/review-feedback matches the contract's 200 Chef feedback shape")
    void shouldConformToChefReviewFeedbackContract() throws Exception {
        when(chefReviewFeedbackService.getFeedback(dishId)).thenReturn(
                new ChefReviewFeedbackResponse(dishId, DishStatus.DRAFT, List.of()));

        mockMvc.perform(get("/api/v1/dishes/{id}/review-feedback", dishId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dishId").value(dishId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.entries").isArray());
    }
}
