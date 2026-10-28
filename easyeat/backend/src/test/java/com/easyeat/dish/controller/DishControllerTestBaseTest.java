package com.easyeat.dish.controller;

import com.easyeat.common.context.TenantContextHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(DishControllerTestBaseTest.TestDummyController.class)
@Import(DishControllerTestBaseTest.TestDummyController.class)
class DishControllerTestBaseTest extends DishControllerTestBase {

    @RestController
    static class TestDummyController {
        @GetMapping("/api/v1/test/dummy-problem")
        public ResponseEntity<ProblemDetail> getDummyProblem() {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Test detail message");
            problem.setType(URI.create("https://easyeat.com/errors/test-error"));
            problem.setTitle("Test Title");
            problem.setInstance(URI.create("/api/v1/test/dummy-problem"));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
        }
    }

    @Test
    @DisplayName("Should serialize object to JSON using inherited ObjectMapper")
    void toJsonTest() {
        Map<String, String> payload = Map.of("name", "Risotto", "category", "Platos principales");
        String json = toJson(payload);

        assertThat(json).contains("\"name\":\"Risotto\"");
        assertThat(json).contains("\"category\":\"Platos principales\"");
    }

    @Test
    @DisplayName("Should manage TenantContextHolder lifecycle")
    void tenantContextLifecycle() {
        UUID customTenant = UUID.randomUUID();
        setTenantContext(customTenant);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(customTenant);

        tearDownTenantContext();
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("Should verify assertProblemDetail helper against RFC 7807 problem responses")
    void assertProblemDetailHelper() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/v1/test/dummy-problem"));

        assertProblemDetail(result, 400, "https://easyeat.com/errors/test-error", "Test Title");
    }
}
