package com.easyeat.operations;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An administrator whose tenant has no row in {@code establishments} (the dev-seed case of
 * laura.admin@cafeparque.com) must get a truthful 404, not a 401 that logs them out or a 409
 * that blames a concurrent administrator.
 */
@AutoConfigureMockMvc
@DisplayName("Saving the operating configuration for a tenant with no establishment")
class AdminWithoutEstablishmentTest extends OperationsPostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor adminOfUnknownTenant() {
        var user = new User("orphan.admin@easyeat.test", "hash", UserRole.ADMINISTRADOR, UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private void expectNotFound(String url, String body) throws Exception {
        mockMvc.perform(put(url).with(adminOfUnknownTenant()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("business hours answer 404")
    void schedules() throws Exception {
        expectNotFound("/api/v1/admin/operating-schedules",
                "[{\"dayOfWeek\":\"MONDAY\",\"openingTime\":\"11:00:00\",\"closingTime\":\"16:00:00\",\"isClosed\":false}]");
    }

    @Test
    @DisplayName("slot capacities answer 404")
    void capacities() throws Exception {
        expectNotFound("/api/v1/admin/slot-capacities",
                "[{\"slotDate\":\"2026-11-09\",\"startTime\":\"12:00:00\",\"maxCapacity\":10}]");
    }

    @Test
    @DisplayName("payment methods answer 404, not a concurrency conflict")
    void paymentMethods() throws Exception {
        expectNotFound("/api/v1/admin/payment-methods/STRIPE", "{\"isEnabled\":false}");
    }
}
