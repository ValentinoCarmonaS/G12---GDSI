package com.easyeat.operations.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.common.filter.TenantContextFilter;
import com.easyeat.config.MethodSecurityConfig;
import com.easyeat.config.SecurityConfig;
import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.domain.EstablishmentSlotCapacity;
import com.easyeat.operations.domain.TimeSlot;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.easyeat.operations.repository.SlotCapacityRepository;
import com.easyeat.operations.service.DinerTimeSlotService;
import com.easyeat.operations.service.EstablishmentTimeProvider;
import com.easyeat.operations.service.OperatingScheduleService;
import com.easyeat.operations.service.SlotCapacityService;
import com.easyeat.operations.exception.SlotSoldOutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
@WebMvcTest({AdminOperatingScheduleController.class, AdminSlotCapacityController.class, DinerTimeSlotController.class})
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationFilter.class,
        TenantContextFilter.class,
        OperatingScheduleService.class,
        SlotCapacityService.class,
        DinerTimeSlotService.class
})
@DisplayName("Admin operations controllers")
class AdminOperationsControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SCHEDULES = "/api/v1/admin/operating-schedules";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private OperatingScheduleRepository scheduleRepository;

    @MockBean
    private SlotCapacityRepository capacityRepository;

    @MockBean
    private EstablishmentTimeProvider timeProvider;

    /**
     * Authenticates as a user of {@code TENANT}, the way TenantContextFilter reads the tenant. The
     * granted authority is what the role gate checks, so it is the only thing that varies.
     */
    private static RequestPostProcessor as(String role) {
        var user = new User("user@easyeat.test", "hash", UserRole.ADMINISTRADOR, TENANT);
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
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
                @DisplayName("a non-admin role is refused with 403")
        void shouldReturn403ForNonAdmin() throws Exception {
            mockMvc.perform(get(SCHEDULES).with(as("CHEF"))).andExpect(status().isForbidden());
            mockMvc.perform(put(SCHEDULES).with(as("CHEF")).contentType(MediaType.APPLICATION_JSON)
                    .content(body("11:00:00", "16:00:00", false, "null"))).andExpect(status().isForbidden());
        }

        @Test
                @DisplayName("shouldSaveSchedule: PUT stores the week and answers 200 with the contract keys")
        void shouldSaveSchedule() throws Exception {
            when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(TENANT, DayOfWeek.MONDAY)).thenReturn(Optional.empty());
            when(scheduleRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
                List<EstablishmentOperatingSchedule> saved = invocation.getArgument(0);
                saved.forEach(s -> ReflectionTestUtils.setField(s, "id", UUID.randomUUID()));
                saved.forEach(s -> ReflectionTestUtils.setField(s, "version", 0L));
                return saved;
            });

            mockMvc.perform(put(SCHEDULES).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
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
                @DisplayName("the ADMINISTRADOR alias is admitted")
        void shouldAdmitAdministradorAlias() throws Exception {
            when(scheduleRepository.findByEstablishmentId(TENANT)).thenReturn(List.of());

            mockMvc.perform(get(SCHEDULES).with(as("ADMINISTRADOR")))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
                @DisplayName("INV-7: opening not before closing is a 400 ProblemDetail")
        void shouldRejectOpeningAfterClosing() throws Exception {
            mockMvc.perform(put(SCHEDULES).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                            .content(body("17:00:00", "15:00:00", false, "null")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Parámetros de horario inválidos"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value(
                            "La hora de apertura (17:00) debe ser estrictamente anterior a la hora de cierre (15:00)."))
                    .andExpect(jsonPath("$.instance").exists());
        }

        @Test
                @DisplayName("a body missing a required field is a 400")
        void shouldRejectMissingFields() throws Exception {
            mockMvc.perform(put(SCHEDULES).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"dayOfWeek\":\"MONDAY\"}]"))
                    .andExpect(status().isBadRequest());
        }

        @Test
                @DisplayName("EC-012: a stale version is a 409 ProblemDetail")
        void shouldFailOnOptimisticLockConflict() throws Exception {
            when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(TENANT, DayOfWeek.MONDAY))
                    .thenReturn(Optional.of(monday(4L)));

            mockMvc.perform(put(SCHEDULES).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                            .content(body("12:00:00", "18:00:00", false, "3")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Conflicto de concurrencia"))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value(
                            "La configuración fue modificada por otro administrador. Por favor recargue la pantalla para ver los cambios."));
        }

        @Test
                @DisplayName("GET returns the configured days, scoped to the tenant of the session")
        void shouldReturnConfiguredWeek() throws Exception {
            when(scheduleRepository.findByEstablishmentId(TENANT)).thenReturn(List.of(monday(2L)));

            mockMvc.perform(get(SCHEDULES).with(as("ADMIN")))
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
            // US2: a slot is offered as AVAILABLE only if it has capacity (EC-007)
            LocalDate today = LocalDate.of(2026, 11, 10);
            when(capacityRepository.findByEstablishmentIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(TENANT, today, today))
                    .thenReturn(List.of(
                            new EstablishmentSlotCapacity(TENANT, new TimeSlot(today, LocalTime.of(15, 30)), 10),
                            new EstablishmentSlotCapacity(TENANT, new TimeSlot(today, LocalTime.of(15, 45)), 10)));
        }

        @Test
        @DisplayName("an unauthenticated request is refused with 401 (contract)")
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(get(slotsUrl(TENANT)).param("date", "2026-11-10")).andExpect(status().isUnauthorized());
        }

        @Test
                @DisplayName("any authenticated role gets only the admissible slots of today")
        void shouldReturnOnlyAdmissibleSlots() throws Exception {
            mockMvc.perform(get(slotsUrl(TENANT)).with(as("COMENSAL")).param("date", "2026-11-10"))
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
                @DisplayName("an unconfigured day answers 200 isClosed with no slots")
        void shouldReturnClosedForUnconfiguredDay() throws Exception {
            when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(TENANT, DayOfWeek.THURSDAY)).thenReturn(Optional.empty());

            mockMvc.perform(get(slotsUrl(TENANT)).with(as("COMENSAL")).param("date", "2026-11-12"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isClosed").value(true))
                    .andExpect(jsonPath("$.closureReason").value("Cocina cerrada para este día"))
                    .andExpect(jsonPath("$.slots.length()").value(0));
        }

        @Test
                @DisplayName("a missing or malformed date is a 400")
        void shouldRejectBadDate() throws Exception {
            mockMvc.perform(get(slotsUrl(TENANT)).with(as("COMENSAL"))).andExpect(status().isBadRequest());
            mockMvc.perform(get(slotsUrl(TENANT)).with(as("COMENSAL")).param("date", "10/11/2026")).andExpect(status().isBadRequest());
        }

        @Test
                @DisplayName("an unknown establishment is a 404")
        void shouldReturn404ForUnknownEstablishment() throws Exception {
            UUID unknown = UUID.randomUUID();
            when(timeProvider.nowAt(unknown)).thenThrow(new com.easyeat.catalog.exception.EstablishmentNotFoundException(unknown));

            mockMvc.perform(get(slotsUrl(unknown)).with(as("COMENSAL")).param("date", "2026-11-10")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("US2 - slot capacity")
    class Capacity {

        private static final String CAPACITIES = "/api/v1/admin/slot-capacities";
        private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 11);

        private String slot(String start, String maxCapacity, String expectedVersion) {
            return """
                    {"slotDate":"2026-11-11","startTime":"%s","maxCapacity":%s,"expectedVersion":%s}""".formatted(start, maxCapacity, expectedVersion);
        }

        private EstablishmentSlotCapacity stored(LocalTime start, int max, int approved, long version) {
            var capacity = new EstablishmentSlotCapacity(TENANT, new TimeSlot(WEDNESDAY, start), max, approved);
            ReflectionTestUtils.setField(capacity, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(capacity, "version", version);
            return capacity;
        }

        @Test
        @DisplayName("an unauthenticated request is refused with 401")
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(get(CAPACITIES).param("date", "2026-11-11")).andExpect(status().isUnauthorized());
            mockMvc.perform(put(CAPACITIES).contentType(MediaType.APPLICATION_JSON).content("[" + slot("12:00:00", "5", "null") + "]"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a non-admin role is refused with 403")
        void shouldReturn403ForNonAdmin() throws Exception {
            mockMvc.perform(get(CAPACITIES).param("date", "2026-11-11").with(as("CHEF"))).andExpect(status().isForbidden());
            mockMvc.perform(put(CAPACITIES).with(as("COMENSAL")).contentType(MediaType.APPLICATION_JSON)
                    .content("[" + slot("12:00:00", "5", "null") + "]")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("shouldUpdateCapacity: PUT stores the slot and answers 200 with the contract keys")
        void shouldUpdateCapacity() throws Exception {
            when(capacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(any(), any(), any())).thenReturn(Optional.empty());
            when(capacityRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
                List<EstablishmentSlotCapacity> saved = invocation.getArgument(0);
                saved.forEach(c -> ReflectionTestUtils.setField(c, "id", UUID.randomUUID()));
                saved.forEach(c -> ReflectionTestUtils.setField(c, "version", 0L));
                return saved;
            });

            mockMvc.perform(put(CAPACITIES).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                            .content("[" + slot("12:00:00", "50", "null") + "]"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].slotDate").value("2026-11-11"))
                    .andExpect(jsonPath("$[0].startTime").value("12:00:00"))
                    .andExpect(jsonPath("$[0].endTime").value("12:15:00"))
                    .andExpect(jsonPath("$[0].maxCapacity").value(50))
                    .andExpect(jsonPath("$[0].approvedOrdersCount").value(0))
                    .andExpect(jsonPath("$[0].isSoldOut").value(false))
                    .andExpect(jsonPath("$[0].soldOut").doesNotExist())
                    .andExpect(jsonPath("$[0].version").value(0));
        }

        @Test
        @DisplayName("SC-005: one PUT carries many slots (bulk apply) and they are saved together")
        void shouldAcceptManySlotsInOnePut() throws Exception {
            when(capacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(any(), any(), any())).thenReturn(Optional.empty());
            when(capacityRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
                List<EstablishmentSlotCapacity> saved = invocation.getArgument(0);
                saved.forEach(c -> ReflectionTestUtils.setField(c, "version", 0L));
                return saved;
            });
            String body = "[" + slot("12:00:00", "20", "null") + "," + slot("12:15:00", "20", "null") + ","
                    + slot("12:30:00", "20", "null") + "," + slot("12:45:00", "20", "null") + "]";

            mockMvc.perform(put(CAPACITIES).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4));
            verify(capacityRepository, times(1)).saveAllAndFlush(any());
        }

        @Test
        @DisplayName("INV-6: a negative capacity is a 400 'Capacidad inválida'")
        void shouldRejectNegativeCapacity() throws Exception {
            mockMvc.perform(put(CAPACITIES).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                            .content("[" + slot("12:00:00", "-1", "null") + "]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Capacidad inválida"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("La capacidad máxima por franja debe ser un número entero mayor o igual a 0."));
        }

        @Test
        @DisplayName("FR-007: a non-integer capacity is a 400")
        void shouldRejectNonIntegerCapacity() throws Exception {
            mockMvc.perform(put(CAPACITIES).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                            .content("[" + slot("12:00:00", "5.5", "null") + "]"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("INV-5: a start that is not on a 15-minute boundary is a 400 'Capacidad inválida'")
        void shouldRejectMisalignedStart() throws Exception {
            mockMvc.perform(put(CAPACITIES).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                            .content("[" + slot("12:07:00", "5", "null") + "]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Capacidad inválida"));
        }

        @Test
        @DisplayName("a body missing a required field is a 400")
        void shouldRejectMissingFields() throws Exception {
            mockMvc.perform(put(CAPACITIES).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"slotDate\":\"2026-11-11\"}]"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("EC-012: a stale version is a 409 'Conflicto de concurrencia'")
        void shouldFailOnStaleVersion() throws Exception {
            when(capacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(TENANT, WEDNESDAY, LocalTime.of(12, 0)))
                    .thenReturn(Optional.of(stored(LocalTime.of(12, 0), 50, 0, 4)));

            mockMvc.perform(put(CAPACITIES).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                            .content("[" + slot("12:00:00", "30", "3") + "]"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Conflicto de concurrencia"));
        }

        @Test
        @DisplayName("GET returns the day matrix: unconfigured slots are capacity 0, sold out, without id or version")
        void shouldReturnDayMatrix() throws Exception {
            when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(TENANT, DayOfWeek.WEDNESDAY)).thenReturn(Optional.of(
                    new EstablishmentOperatingSchedule(TENANT, DayOfWeek.WEDNESDAY, LocalTime.of(12, 0), LocalTime.of(13, 0), false)));
            when(capacityRepository.findByEstablishmentIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(TENANT, WEDNESDAY, WEDNESDAY))
                    .thenReturn(List.of(stored(LocalTime.of(12, 15), 20, 20, 2)));

            mockMvc.perform(get(CAPACITIES).param("date", "2026-11-11").with(as("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andExpect(jsonPath("$[0].startTime").value("12:00:00"))
                    .andExpect(jsonPath("$[0].id").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$[0].version").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$[0].maxCapacity").value(0))
                    .andExpect(jsonPath("$[0].isSoldOut").value(true))
                    .andExpect(jsonPath("$[1].maxCapacity").value(20))
                    .andExpect(jsonPath("$[1].approvedOrdersCount").value(20))
                    .andExpect(jsonPath("$[1].isSoldOut").value(true))
                    .andExpect(jsonPath("$[1].version").value(2));
        }

        @Test
        @DisplayName("GET without a valid date is a 400")
        void shouldRejectBadDate() throws Exception {
            mockMvc.perform(get(CAPACITIES).with(as("ADMIN"))).andExpect(status().isBadRequest());
            mockMvc.perform(get(CAPACITIES).param("date", "11-11-2026").with(as("ADMIN"))).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("INV-3 / EC-007: the diner endpoint shows SOLD_OUT for a full slot and for a slot without capacity")
        void shouldShowSoldOutToDiner() throws Exception {
            LocalDate today = LocalDate.of(2026, 11, 10);
            ZonedDateTime now = ZonedDateTime.of(2026, 11, 10, 15, 0, 0, 0, ZoneId.of("America/Argentina/Buenos_Aires"));
            when(timeProvider.nowAt(TENANT)).thenReturn(now);
            when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(TENANT, DayOfWeek.TUESDAY)).thenReturn(Optional.of(
                    new EstablishmentOperatingSchedule(TENANT, DayOfWeek.TUESDAY, LocalTime.of(11, 0), LocalTime.of(16, 0), false)));
            when(capacityRepository.findByEstablishmentIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(TENANT, today, today))
                    .thenReturn(List.of(
                            new EstablishmentSlotCapacity(TENANT, new TimeSlot(today, LocalTime.of(15, 0)), 10, 10),
                            new EstablishmentSlotCapacity(TENANT, new TimeSlot(today, LocalTime.of(15, 15)), 10, 9)));

            mockMvc.perform(get("/api/v1/establishments/" + TENANT + "/time-slots").param("date", "2026-11-10").with(as("COMENSAL")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slots[0].startTime").value("15:00:00"))
                    .andExpect(jsonPath("$.slots[0].status").value("SOLD_OUT"))
                    .andExpect(jsonPath("$.slots[1].status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.slots[2].startTime").value("15:30:00"))
                    .andExpect(jsonPath("$.slots[2].status").value("SOLD_OUT"));
        }

        @Test
        @DisplayName("INV-2 / EC-016: SlotSoldOutException is a 409 'Franja horaria agotada'")
        void shouldMapSoldOutTo409() {
            var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/orders");
            var response = new GlobalExceptionHandler().handleSlotSoldOut(new SlotSoldOutException(50), request);

            org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(409);
            org.assertj.core.api.Assertions.assertThat(response.getBody().getTitle()).isEqualTo("Franja horaria agotada");
            org.assertj.core.api.Assertions.assertThat(response.getBody().getDetail())
                    .isEqualTo("La franja seleccionada ha alcanzado su capacidad máxima permitida (50 pedidos).");
        }
    }
}
