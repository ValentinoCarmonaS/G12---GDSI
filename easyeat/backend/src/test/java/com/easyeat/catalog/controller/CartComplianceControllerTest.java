package com.easyeat.catalog.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.catalog.dto.CartComplianceRequest;
import com.easyeat.catalog.dto.CartComplianceResponse;
import com.easyeat.catalog.dto.CartItemEvaluation;
import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.catalog.service.CatalogComplianceService;
import com.easyeat.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartComplianceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
@DisplayName("CartComplianceController Web Contract Tests (T041, US3, FR-015, FR-021)")
class CartComplianceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CatalogComplianceService catalogComplianceService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    private User sampleDiner;

    @BeforeEach
    void setUp() {
        sampleDiner = new User();
        sampleDiner.setId(UUID.randomUUID());
        sampleDiner.setEmail("diner.cart@easyeat.com");
        sampleDiner.setRole(UserRole.COMENSAL);
        sampleDiner.setStatus(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Authenticated request: POST /api/v1/cart/evaluate-compliance returns 200 with evaluated items")
    void evaluateCompliance_authenticated_returns200() throws Exception {
        UUID dish1Id = UUID.randomUUID();
        UUID dish2Id = UUID.randomUUID();

        CartComplianceRequest request = new CartComplianceRequest(List.of(dish1Id, dish2Id));
        CartComplianceResponse response = new CartComplianceResponse(List.of(
                new CartItemEvaluation(dish1Id, true, Collections.emptyList()),
                new CartItemEvaluation(dish2Id, false, List.of("PEANUTS"))
        ));

        when(catalogComplianceService.evaluateCartCompliance(any(CartComplianceRequest.class), any(User.class)))
                .thenReturn(response);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                sampleDiner,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );

        mockMvc.perform(post("/api/v1/cart/evaluate-compliance")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].dishId").value(dish1Id.toString()))
                .andExpect(jsonPath("$.items[0].isCompliant").value(true))
                .andExpect(jsonPath("$.items[0].conflictingRestrictions").isEmpty())
                .andExpect(jsonPath("$.items[1].dishId").value(dish2Id.toString()))
                .andExpect(jsonPath("$.items[1].isCompliant").value(false))
                .andExpect(jsonPath("$.items[1].conflictingRestrictions[0]").value("PEANUTS"));

        verify(catalogComplianceService).evaluateCartCompliance(any(CartComplianceRequest.class), any(User.class));
    }

    @Test
    @DisplayName("Unauthenticated request: POST /api/v1/cart/evaluate-compliance returns 401 Unauthorized")
    void evaluateCompliance_unauthenticated_returns401() throws Exception {
        CartComplianceRequest request = new CartComplianceRequest(List.of(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/cart/evaluate-compliance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(catalogComplianceService);
    }

    @Test
    @DisplayName("Validation error: empty dishIds list returns 400 Bad Request")
    void evaluateCompliance_emptyDishIds_returns400() throws Exception {
        CartComplianceRequest request = new CartComplianceRequest(Collections.emptyList());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                sampleDiner,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );

        mockMvc.perform(post("/api/v1/cart/evaluate-compliance")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(catalogComplianceService);
    }

    @Test
    @DisplayName("Dish not found: service throwing DishNotFoundException returns 404 ProblemDetail")
    void evaluateCompliance_dishNotFound_returns404() throws Exception {
        UUID unknownDishId = UUID.randomUUID();
        CartComplianceRequest request = new CartComplianceRequest(List.of(unknownDishId));

        when(catalogComplianceService.evaluateCartCompliance(any(CartComplianceRequest.class), any(User.class)))
                .thenThrow(new DishNotFoundException(unknownDishId));

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                sampleDiner,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );

        mockMvc.perform(post("/api/v1/cart/evaluate-compliance")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Dish Not Found"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(unknownDishId.toString())));
    }
}
