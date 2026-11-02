package com.easyeat.operations.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.common.filter.TenantContextFilter;
import com.easyeat.config.MethodSecurityConfig;
import com.easyeat.config.SecurityConfig;
import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.easyeat.operations.service.DinerTimeSlotService;
import com.easyeat.operations.service.EstablishmentTimeProvider;
import com.easyeat.operations.service.OperatingScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer of the restaurant operation configuration. Created with the schedule section (US1);
 * the capacity and payment sections are added by US2 and US3, which is why it is one class and
 * not one per story.
 */
@WebMvcTest({AdminOperatingScheduleController.class, DinerTimeSlotController.class})
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationFilter.class,
        TenantContextFilter.class,
        OperatingScheduleService.class,
        DinerTimeSlotService.class
})
@DisplayName("Admin operations controllers")
class AdminOperationsControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SCHEDULES = "/api/v1/admin/operating-schedules";
    private static final String BEARER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private OperatingScheduleRepository scheduleRepository;

    @MockBean
    private EstablishmentTimeProvider timeProvider;

    @BeforeEach
    void tenant() {
        when(jwtService.extractTenantId("test-token")).thenReturn(TENANT);
    }

    private static EstablishmentOperatingSchedule monday(long version) {
        var schedule = new EstablishmentOperatingSchedule(TENANT, DayOfWeek.MONDAY,
                LocalTime.of(11, 0), LocalTime.of(16, 0), false);
        ReflectionTestUtils.setField(schedule, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(schedule, "version", version);
        return schedule;
    }

    private static String body(String openingTime, String closingTime, boolean closed, String expectedVersion) {
        return """
                [{"dayOfWeek":"MONDAY","openingTime":"%s","closingTime":"%s","isClosed":%s,"expectedVersion":%s}]
                """.formatted(openingTime, closingTime, closed, expectedVersion);
    }

    @Nested
    @DisplayName("US1 - schedules")
    class Schedules {

        @Test
        @DisplayName("an unauthenticated request is refused with 401")
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(get(SCHEDULES)).andExpect(status().isUnauthorized());
            mockMvc.perform(put(SCHEDULES).contentType(MediaType.APPLICATION_JSON)
                    .content(body("11:00:00", "16:00:00", false, "null"))).andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("a non-admin role is refused with 403")
        void shouldReturn403ForNonAdmin() throws Exception {
            mockMvc.perform(get(SCHEDULES).header("Authorization", BEARER)).andExpect(status().isForbidden());
            mockMvc.perform(put(SCHEDULES).header("Authorization", BEARER).contentType(MediaType.APPLICATION_JSON)
                    .content(body("11:00:00", "16:00:00", false, "null"))).andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldSaveSchedule: PUT stores the week and answers 200 with the contract keys")
        void shouldSaveSchedule() throws Exception {
            when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(TENANT, DayOfWeek.MONDAY)).thenReturn(Optional.empty());
            when(scheduleRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
                List<EstablishmentOperatingSchedule> saved = invocation.getArgument(0);
                saved.forEach(s -> ReflectionTestUtils.setField(s, "id", UUID.randomUUID()));
                saved.forEach(s -> ReflectionTestUtils.setField(s, "version", 0L));
                return saved;
            });

            mockMvc.perform(put(SCHEDULES).header("Authorization", BEARER).contentType(MediaType.APPLICATION_JSON)
                            .content(body("11:00:00", "16:00:00", false, "null")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"))
                    .andExpect(jsonPath("$[0].openingTime").value("11:00:00"))
                    .andExpect(jsonPath("$[0].closingTime").value("16:00:00"))
                    .andExpect(jsonPath("$[0].isClosed").value(false))
                    .andExpect(jsonPath("$[0].closed").doesNotExist())
                    .andExpect(jsonPath("$[0].version").value(0))
                    .andExpect(jsonPath("$[0].id").exists());
        }

        @Test
        @WithMockUser(roles = "ADMINISTRADOR")
        @DisplayName("the ADMINISTRADOR alias is admitted")
        void shouldAdmitAdministradorAlias() throws Exception {
            when(scheduleRepository.findByEstablishmentId(TENANT)).thenReturn(List.of());

            mockMvc.perform(get(SCHEDULES).header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("INV-7: opening not before closing is a 400 ProblemDetail")
        void shouldRejectOpeningAfterClosing() throws Exception {
            mockMvc.perform(put(SCHEDULES).header("Authorization", BEARER).contentType(MediaType.APPLICATION_JSON)
                            .content(body("17:00:00", "15:00:00", false, "null")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Parámetros de horario inválidos"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value(
                            "La hora de apertura (17:00) debe ser estrictamente anterior a la hora de cierre (15:00)."))
                    .andExpect(jsonPath("$.instance").exists());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("a body missing a required field is a 400")
        void shouldRejectMissingFields() throws Exception {
            mockMvc.perform(put(SCHEDULES).header("Authorization", BEARER).contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"dayOfWeek\":\"MONDAY\"}]"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("EC-012: a stale version is a 409 ProblemDetail")
        void shouldFailOnOptimisticLockConflict() throws Exception {
            when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(TENANT, DayOfWeek.MONDAY))
                    .thenReturn(Optional.of(monday(4L)));

            mockMvc.perform(put(SCHEDULES).header("Authorization", BEARER).contentType(MediaType.APPLICATION_JSON)
                            .content(body("12:00:00", "18:00:00", false, "3")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Conflicto de concurrencia"))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value(
                            "La configuración fue modificada por otro administrador. Por favor recargue la pantalla para ver los cambios."));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("GET returns the configured days, scoped to the tenant of the session")
        void shouldReturnConfiguredWeek() throws Exception {
            when(scheduleRepository.findByEstablishmentId(TENANT)).thenReturn(List.of(monday(2L)));

            mockMvc.perform(get(SCHEDULES).header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"))
                    .andExpect(jsonPath("$[0].isClosed").value(false))
                    .andExpect(jsonPath("$[0].version").value(2));
        }
    }

    @Nested
    @DisplayName("US1 - diner time slots")
    class DinerTimeSlots {

        private String slotsUrl(UUID tenant) {
            return "/api/v1/establishments/" + tenant + "/time-slots";
        }

        @BeforeEach
        void clockAndSchedule() {
            ZonedDateTime now = ZonedDateTime.of(2026, 11, 10, 15, 30, 0, 0, ZoneId.of("America/Argentina/Buenos_Aires"));
            when(timeProvider.nowAt(TENANT)).thenReturn(now);
            when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(TENANT, DayOfWeek.TUESDAY)).thenReturn(Optional.of(
                    new EstablishmentOperatingSchedule(TENANT, DayOfWeek.TUESDAY, LocalTime.of(11, 0), LocalTime.of(16, 0), false)));
        }

        @Test
        @DisplayName("an unauthenticated request is refused with 401 (contract)")
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(get(slotsUrl(TENANT)).param("date", "2026-11-10")).andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("any authenticated role gets only the admissible slots of today")
        void shouldReturnOnlyAdmissibleSlots() throws Exception {
            mockMvc.perform(get(slotsUrl(TENANT)).param("date", "2026-11-10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.date").value("2026-11-10"))
                    .andExpect(jsonPath("$.isClosed").value(false))
                    .andExpect(jsonPath("$.closed").doesNotExist())
                    .andExpect(jsonPath("$.timezone").value("America/Argentina/Buenos_Aires"))
                    .andExpect(jsonPath("$.slots.length()").value(2))
                    .andExpect(jsonPath("$.slots[0].startTime").value("15:30:00"))
                    .andExpect(jsonPath("$.slots[0].endTime").value("15:45:00"))
                    .andExpect(jsonPath("$.slots[0].status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.slots[1].startTime").value("15:45:00"));
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("an unconfigured day answers 200 isClosed with no slots")
        void shouldReturnClosedForUnconfiguredDay() throws Exception {
            when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(TENANT, DayOfWeek.THURSDAY)).thenReturn(Optional.empty());

            mockMvc.perform(get(slotsUrl(TENANT)).param("date", "2026-11-12"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isClosed").value(true))
                    .andExpect(jsonPath("$.closureReason").value("Cocina cerrada para este día"))
                    .andExpect(jsonPath("$.slots.length()").value(0));
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("a missing or malformed date is a 400")
        void shouldRejectBadDate() throws Exception {
            mockMvc.perform(get(slotsUrl(TENANT))).andExpect(status().isBadRequest());
            mockMvc.perform(get(slotsUrl(TENANT)).param("date", "10/11/2026")).andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("an unknown establishment is a 404")
        void shouldReturn404ForUnknownEstablishment() throws Exception {
            UUID unknown = UUID.randomUUID();
            when(timeProvider.nowAt(unknown)).thenThrow(new com.easyeat.catalog.exception.EstablishmentNotFoundException(unknown));

            mockMvc.perform(get(slotsUrl(unknown)).param("date", "2026-11-10")).andExpect(status().isNotFound());
        }
    }
}
