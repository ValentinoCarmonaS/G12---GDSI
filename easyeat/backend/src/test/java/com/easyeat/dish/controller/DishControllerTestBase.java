package com.easyeat.dish.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.config.MethodSecurityConfig;
import com.easyeat.config.SecurityConfig;
import com.easyeat.dish.DishTestFixtures;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base web test support class for Dish module controllers.
 * Provides security mock beans, ObjectMapper serialization, tenant setup,
 * and RFC 7807 ProblemDetail assertion helpers.
 */
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationFilter.class
})
public abstract class DishControllerTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockBean
    protected JwtService jwtService;

    @MockBean
    protected UserRepository userRepository;

    protected final UUID defaultTenantId = DishTestFixtures.DEFAULT_TENANT_ID;
    protected final UUID defaultChefId = DishTestFixtures.DEFAULT_CHEF_ID;
    protected final UUID defaultAdminId = DishTestFixtures.DEFAULT_ADMIN_ID;

    @AfterEach
    void tearDownTenantContext() {
        TenantContextHolder.clear();
    }

    /**
     * Serializes an object to JSON string.
     */
    protected String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Asserts RFC 7807 / RFC 9457 ProblemDetail response structure.
     */
    protected ResultActions assertProblemDetail(ResultActions actions, int expectedStatus, String expectedType, String expectedTitle) throws Exception {
        return actions
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.type").value(expectedType))
                .andExpect(jsonPath("$.title").value(expectedTitle))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.instance").exists());
    }

    /**
     * Sets tenant context in TenantContextHolder for unit/mock controller tests.
     */
    protected void setTenantContext(UUID tenantId) {
        TenantContextHolder.setTenantId(tenantId);
    }
}
