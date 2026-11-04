package com.easyeat.operations;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 010 owns {@code /time-slots} and spec 012 owns {@code /pickup-slots}. Each route must reach
 * its own controller: when both controllers mapped {@code /time-slots}, Spring could not choose one
 * and every request answered 409 "Ambiguous handler methods". The field checks tell the two
 * response shapes apart.
 */
@AutoConfigureMockMvc
@DisplayName("The diner slot routes of specs 010 and 012 coexist")
class DinerSlotRoutesCoexistTest extends OperationsPostgresTestBase {

    private static final String ESTABLISHMENT = "/api/v1/establishments/" + CAFE_ID;

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor diner() {
        var user = new User("routes.diner@easyeat.test", "hash", UserRole.COMENSAL, null);
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))));
    }

    private static String tomorrow() {
        return LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1).toString();
    }

    @Test
    @DisplayName("/time-slots answers with the spec 010 response")
    void timeSlotsReachesSpec010() throws Exception {
        mockMvc.perform(get(ESTABLISHMENT + "/time-slots").param("date", tomorrow()).with(diner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isClosed").exists())
                .andExpect(jsonPath("$.isOperatingDay").doesNotExist());
    }

    @Test
    @DisplayName("/pickup-slots answers with the spec 012 response")
    void pickupSlotsReachesSpec012() throws Exception {
        mockMvc.perform(get(ESTABLISHMENT + "/pickup-slots").param("date", tomorrow()).with(diner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOperatingDay").exists())
                .andExpect(jsonPath("$.isClosed").doesNotExist());
    }
}
