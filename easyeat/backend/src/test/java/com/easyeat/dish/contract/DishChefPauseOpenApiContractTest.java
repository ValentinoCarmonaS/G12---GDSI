package com.easyeat.dish.contract;

import com.easyeat.catalog.dto.DishAvailabilityDetailsResponse;
import com.easyeat.dish.controller.DishController;
import com.easyeat.dish.controller.DishControllerTestBase;
import com.easyeat.dish.dto.ChefDishesOperationalStatusResponse;
import com.easyeat.dish.dto.DishChefPauseResponse;
import com.easyeat.dish.dto.ToggleChefPauseRequest;
import com.easyeat.dish.service.DishService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DishController.class)
@DisplayName("T067: Dish Chef Pause OpenAPI Contract Test (specs/013-critical-stock-chef-pause/contracts/openapi.yaml, D-005)")
class DishChefPauseOpenApiContractTest extends DishControllerTestBase {

    @MockBean
    private DishService dishService;

    private final UUID dishId = UUID.randomUUID();

    @Test
    @DisplayName("OpenAPI contract file exists and defines chef pause and operational-status operations")
    void shouldVerifyOpenApiContractFileDefinesChefPauseOperations() throws Exception {
        Path contractPath = Paths.get("../../specs/013-critical-stock-chef-pause/contracts/openapi.yaml")
                .toAbsolutePath().normalize();
        if (!Files.exists(contractPath)) {
            contractPath = Paths.get("specs/013-critical-stock-chef-pause/contracts/openapi.yaml")
                    .toAbsolutePath().normalize();
        }

        assertThat(Files.exists(contractPath))
                .withFailMessage("OpenAPI contract file not found at " + contractPath)
                .isTrue();

        String contractYaml = Files.readString(contractPath);
        assertThat(contractYaml).contains("openapi: 3.0.3");
        assertThat(contractYaml).contains("/api/v1/dishes/{id}/chef-pause:");
        assertThat(contractYaml).contains("/api/v1/dishes/operational-status:");
        assertThat(contractYaml).contains("/api/v1/dishes/{id}/availability-details:");
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("PATCH /api/v1/dishes/{id}/chef-pause matches OpenAPI 200 response shape")
    void shouldConformToToggleChefPauseContract() throws Exception {
        DishChefPauseResponse response = new DishChefPauseResponse(dishId, true, Instant.now());
        when(dishService.toggleChefPause(eq(dishId), eq(true), any())).thenReturn(response);

        String payload = """
                {
                    "paused": true
                }
                """;

        mockMvc.perform(patch("/api/v1/dishes/{id}/chef-pause", dishId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dishId").value(dishId.toString()))
                .andExpect(jsonPath("$.pausedByChef").value(true))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("GET /api/v1/dishes/operational-status matches OpenAPI 200 response shape with ETag")
    void shouldConformToGetOperationalStatusContract() throws Exception {
        ChefDishesOperationalStatusResponse.ChefDishOperationalStatusItem item =
                new ChefDishesOperationalStatusResponse.ChefDishOperationalStatusItem(
                        dishId,
                        false,
                        true,
                        true,
                        "PUBLISHED"
                );
        ChefDishesOperationalStatusResponse response = new ChefDishesOperationalStatusResponse(
                List.of(item)
        );

        when(dishService.getOperationalStatus(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/dishes/operational-status"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].dishId").value(dishId.toString()))
                .andExpect(jsonPath("$.items[0].pausedByChef").value(false))
                .andExpect(jsonPath("$.items[0].hasStock").value(true))
                .andExpect(jsonPath("$.items[0].isActiveAdmin").value(true));
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("GET /api/v1/dishes/operational-status returns 304 Not Modified when ETag matches")
    void shouldReturn304WhenEtagMatches() throws Exception {
        ChefDishesOperationalStatusResponse.ChefDishOperationalStatusItem item =
                new ChefDishesOperationalStatusResponse.ChefDishOperationalStatusItem(
                        dishId,
                        false,
                        true,
                        true,
                        "PUBLISHED"
                );
        ChefDishesOperationalStatusResponse response = new ChefDishesOperationalStatusResponse(
                List.of(item)
        );

        when(dishService.getOperationalStatus(any())).thenReturn(response);

        StringBuilder sb = new StringBuilder();
        sb.append(dishId).append("=false:true;");
        String expectedEtag = "\"" + Integer.toHexString(sb.toString().hashCode()) + "\"";

        mockMvc.perform(get("/api/v1/dishes/operational-status")
                        .header("If-None-Match", expectedEtag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", expectedEtag));
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("GET /api/v1/dishes/{id}/availability-details matches OpenAPI 200 response shape (INV-10)")
    void shouldConformToGetDishAvailabilityDetailsContract() throws Exception {
        DishAvailabilityDetailsResponse response = new DishAvailabilityDetailsResponse(
                dishId,
                new DishAvailabilityDetailsResponse.UnavailabilityCauses(false, true, false),
                false,
                false
        );

        when(dishService.getDishAvailabilityDetails(eq(dishId), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/dishes/{id}/availability-details", dishId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dishId").value(dishId.toString()))
                .andExpect(jsonPath("$.causes.sinStock").value(false))
                .andExpect(jsonPath("$.causes.pausaChef").value(true))
                .andExpect(jsonPath("$.causes.desactivacionAdmin").value(false));
    }
}
