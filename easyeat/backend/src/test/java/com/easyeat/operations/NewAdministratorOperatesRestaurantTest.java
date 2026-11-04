package com.easyeat.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Any administrator must be able to configure the operation of their restaurant, and only of their
 * restaurant. Runs the real chain with the dev seed: login, staff registration, mandatory password
 * change, and the three sections of the screen. Nothing about security or tenancy is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("A newly created administrator operates their own restaurant, and only it")
class NewAdministratorOperatesRestaurantTest {

    private static final String SCHEDULES = "/api/v1/admin/operating-schedules";
    private static final String CAPACITIES = "/api/v1/admin/slot-capacities";
    private static final String PAYMENTS = "/api/v1/admin/payment-methods";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_new_admin_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        return json(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())).get("access_token").asText();
    }

    private ResultActions putAs(String token, String url, String body) throws Exception {
        return mockMvc.perform(put(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode getAs(String token, String url) throws Exception {
        return json(mockMvc.perform(get(url).header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
    }

    private static String monday(String opening, String closing) {
        return "[{\"dayOfWeek\":\"MONDAY\",\"openingTime\":\"%s\",\"closingTime\":\"%s\",\"isClosed\":false}]"
                .formatted(opening, closing);
    }

    @Test
    @DisplayName("admin@easyeat.com creates an administrator who saves all three sections, isolated from Café Parque")
    void newAdministratorOperatesOnlyTheirRestaurant() throws Exception {
        // 1. The seeded administrator registers a new administrator.
        String founder = login("admin@easyeat.com", "SecurePass123");
        String email = "nueva.admin." + UUID.randomUUID() + "@easyeat.com";
        JsonNode created = json(mockMvc.perform(post("/api/v1/admin/staff")
                        .header("Authorization", "Bearer " + founder).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nueva Admin\",\"email\":\"%s\",\"role\":\"ADMINISTRADOR\"}".formatted(email)))
                .andExpect(status().isCreated()));

        // 2. The new administrator replaces the temporary password and logs in.
        mockMvc.perform(post("/api/v1/auth/first-login-password-change").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"currentPassword\":\"%s\",\"newPassword\":\"ClaveNueva123\",\"confirmNewPassword\":\"ClaveNueva123\"}"
                                .formatted(email, created.get("temporaryCredential").asText())))
                .andExpect(status().isOk());
        String newAdmin = login(email, "ClaveNueva123");

        // 3. Café Parque's administrator (Laura) configures her own restaurant.
        String laura = login("laura.admin@cafeparque.com", "Password123");
        putAs(laura, SCHEDULES, monday("11:00:00", "16:00:00")).andExpect(status().isOk());

        // 4. The new administrator saves the three sections: all must answer 200.
        putAs(newAdmin, SCHEDULES, monday("09:00:00", "10:00:00")).andExpect(status().isOk());
        String date = LocalDate.now().plusDays(7).toString();
        putAs(newAdmin, CAPACITIES, "[{\"slotDate\":\"%s\",\"startTime\":\"12:00:00\",\"maxCapacity\":10}]".formatted(date))
                .andExpect(status().isOk());
        // The screen sends back the version it read; Café Central's rows were seeded by V15.
        putAs(newAdmin, PAYMENTS + "/STRIPE", "{\"isEnabled\":false,\"expectedVersion\":%s}".formatted(stripeVersion(getAs(newAdmin, PAYMENTS))))
                .andExpect(status().isOk());

        // 5. Isolation: each administrator sees only their own restaurant, and neither overwrote the other.
        JsonNode theirs = getAs(newAdmin, SCHEDULES);
        assertThat(theirs).hasSize(1);
        assertThat(theirs.get(0).get("openingTime").asText()).isEqualTo("09:00:00");

        JsonNode lauras = getAs(laura, SCHEDULES);
        assertThat(lauras).hasSize(1);
        assertThat(lauras.get(0).get("openingTime").asText()).isEqualTo("11:00:00");
        assertThat(lauras.get(0).get("id").asText()).isNotEqualTo(theirs.get(0).get("id").asText());

        assertThat(stripeEnabled(getAs(newAdmin, PAYMENTS))).isFalse();
        assertThat(stripeEnabled(getAs(laura, PAYMENTS))).isTrue();
    }

    private static String stripeVersion(JsonNode methods) {
        for (JsonNode method : methods) {
            if ("STRIPE".equals(method.get("paymentMethod").asText())) {
                return method.get("version").isNull() ? "null" : method.get("version").asText();
            }
        }
        throw new AssertionError("STRIPE not listed");
    }

    private static boolean stripeEnabled(JsonNode methods) {
        for (JsonNode method : methods) {
            if ("STRIPE".equals(method.get("paymentMethod").asText())) {
                return method.get("isEnabled").asBoolean();
            }
        }
        throw new AssertionError("STRIPE not listed");
    }
}
