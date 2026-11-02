package com.easyeat.operations;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.operations.domain.TimeSlot;
import com.easyeat.operations.exception.SlotSoldOutException;
import com.easyeat.operations.service.SlotCapacityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the seven scenarios of {@code quickstart.md} against the real application: full Spring
 * context, real endpoints and security, PostgreSQL 16 and a controllable server clock.
 *
 * <p>There is no orders module, so the order steps are exercised through the entry points this
 * feature does ship (see {@code wire-up.md}): approving an order is {@code consumeSlot}, trying to
 * create an order is asking the time slots or {@code validate-payment}.
 */
@AutoConfigureMockMvc
@Import(QuickstartScenariosTest.FixedClockConfig.class)
@DisplayName("Quickstart scenarios 1-7, end to end")
class QuickstartScenariosTest extends OperationsPostgresTestBase {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 3);
    private static final LocalDate WEDNESDAY = TUESDAY.plusDays(1);
    private static final String SCHEDULES = "/api/v1/admin/operating-schedules";
    private static final String CAPACITIES = "/api/v1/admin/slot-capacities";
    private static final String ADMIN_PAYMENTS = "/api/v1/admin/payment-methods";
    private static final String DINER = "/api/v1/establishments/" + CAFE_ID;

    /** The server clock the scenarios move; the application reads it through the {@code Clock} bean. */
    static class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-03T13:00:00Z");

        void setBuenosAires(LocalDate date, LocalTime time) {
            now = date.atTime(time).atZone(BUENOS_AIRES).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableClock clock;

    @Autowired
    private SlotCapacityService capacityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM establishment_operating_schedules WHERE establishment_id = ?", CAFE_ID);
        jdbcTemplate.update("DELETE FROM establishment_slot_capacities WHERE establishment_id = ?", CAFE_ID);
        // a payment-method row that is missing means enabled, so deleting restores the default
        jdbcTemplate.update("DELETE FROM establishment_payment_methods WHERE establishment_id = ?", CAFE_ID);
    }

    private static RequestPostProcessor as(UserRole role, String authority) {
        var user = new User("scenario@easyeat.test", "hash", role, CAFE_ID);
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + authority))));
    }

    private static RequestPostProcessor admin() {
        return as(UserRole.ADMINISTRADOR, "ADMIN");
    }

    private static RequestPostProcessor diner() {
        return as(UserRole.COMENSAL, "COMENSAL");
    }

    private ResultActions putJson(String url, String body) throws Exception {
        return mockMvc.perform(put(url).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode timeSlots(LocalDate date) throws Exception {
        return json(mockMvc.perform(get(DINER + "/time-slots").param("date", date.toString()).with(diner()))
                .andExpect(status().isOk()));
    }

    private JsonNode slotAt(JsonNode response, String startTime) {
        for (JsonNode slot : response.get("slots")) {
            if (startTime.equals(slot.get("startTime").asText())) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Switches a method the way the admin screen does: with the version it read, or none while the
     * method has no row. Establishments that existed when V12 ran already have a row (version 0).
     */
    private void togglePayment(String method, boolean enabled) throws Exception {
        JsonNode methods = json(mockMvc.perform(get(ADMIN_PAYMENTS).with(admin())).andExpect(status().isOk()));
        String expectedVersion = "null";
        for (JsonNode candidate : methods) {
            if (method.equals(candidate.get("paymentMethod").asText()) && !candidate.get("version").isNull()) {
                expectedVersion = candidate.get("version").asText();
            }
        }
        putJson(ADMIN_PAYMENTS + "/" + method, "{\"isEnabled\":%s,\"expectedVersion\":%s}".formatted(enabled, expectedVersion))
                .andExpect(status().isOk());
    }

    private void configureSchedule(String day, String opening, String closing) throws Exception {
        putJson(SCHEDULES, "[{\"dayOfWeek\":\"%s\",\"openingTime\":\"%s\",\"closingTime\":\"%s\",\"isClosed\":false}]"
                .formatted(day, opening, closing)).andExpect(status().isOk());
    }

    private void configureCapacity(LocalDate date, String startTime, int maxCapacity) throws Exception {
        putJson(CAPACITIES, "[{\"slotDate\":\"%s\",\"startTime\":\"%s\",\"maxCapacity\":%d}]"
                .formatted(date, startTime, maxCapacity)).andExpect(status().isOk());
    }

    private void approveOrders(LocalDate date, LocalTime start, int count) {
        for (int i = 0; i < count; i++) {
            capacityService.consumeSlot(CAFE_ID, new TimeSlot(date, start));
        }
    }

    @Test
    @DisplayName("Scenario 1: closing at 16:00 admits 15:59:59, blocks 16:01 and still admits tomorrow 13:00 (AC-1.1, INV-1, EC-017)")
    void scenario1ClosingBlocksTodayButNotTomorrow() throws Exception {
        configureSchedule("TUESDAY", "11:00:00", "16:00:00");
        configureSchedule("WEDNESDAY", "11:00:00", "16:00:00");
        configureCapacity(TUESDAY, "15:45:00", 10);
        configureCapacity(WEDNESDAY, "13:00:00", 10);

        clock.setBuenosAires(TUESDAY, LocalTime.of(15, 59, 59));
        JsonNode lastSecond = timeSlots(TUESDAY);
        assertThat(lastSecond.get("isClosed").asBoolean()).isFalse();
        assertThat(slotAt(lastSecond, "15:45:00").get("status").asText()).isEqualTo("AVAILABLE");

        clock.setBuenosAires(TUESDAY, LocalTime.of(16, 1));
        JsonNode afterClosing = timeSlots(TUESDAY);
        assertThat(afterClosing.get("isClosed").asBoolean()).isTrue();
        assertThat(afterClosing.get("slots")).isEmpty();
        assertThat(afterClosing.get("closureReason").asText()).isEqualTo("Cocina cerrada para el día de hoy");

        clock.setBuenosAires(TUESDAY, LocalTime.of(22, 0));
        JsonNode tomorrow = timeSlots(WEDNESDAY);
        assertThat(tomorrow.get("isClosed").asBoolean()).isFalse();
        assertThat(slotAt(tomorrow, "13:00:00").get("status").asText()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("Scenario 2: the slot stays AVAILABLE at 49 of 50 and is SOLD_OUT at order 50 (AC-2.1, INV-2, INV-3)")
    void scenario2SlotSellsOutAtOrder50() throws Exception {
        configureSchedule("TUESDAY", "11:00:00", "16:00:00");
        configureCapacity(TUESDAY, "12:00:00", 50);
        clock.setBuenosAires(TUESDAY, LocalTime.of(10, 0));

        approveOrders(TUESDAY, LocalTime.of(12, 0), 49);
        assertThat(slotAt(timeSlots(TUESDAY), "12:00:00").get("status").asText()).isEqualTo("AVAILABLE");

        approveOrders(TUESDAY, LocalTime.of(12, 0), 1);
        assertThat(slotAt(timeSlots(TUESDAY), "12:00:00").get("status").asText()).isEqualTo("SOLD_OUT");
        assertThatApprovalIsRefused(TUESDAY, LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("Scenario 3: reducing 50 to 30 with 40 approved keeps the 40 and sells the slot out (INV-8, EC-005)")
    void scenario3ReductionIsNotRetroactive() throws Exception {
        configureSchedule("TUESDAY", "11:00:00", "16:00:00");
        configureCapacity(TUESDAY, "12:00:00", 50);
        clock.setBuenosAires(TUESDAY, LocalTime.of(10, 0));
        approveOrders(TUESDAY, LocalTime.of(12, 0), 40);

        JsonNode before = slotOfMatrix(TUESDAY, "12:00:00");
        long version = before.get("version").asLong();
        putJson(CAPACITIES, "[{\"slotDate\":\"%s\",\"startTime\":\"12:00:00\",\"maxCapacity\":30,\"expectedVersion\":%d}]"
                .formatted(TUESDAY, version)).andExpect(status().isOk());

        JsonNode after = slotOfMatrix(TUESDAY, "12:00:00");
        assertThat(after.get("maxCapacity").asInt()).isEqualTo(30);
        assertThat(after.get("approvedOrdersCount").asInt()).isEqualTo(40);
        assertThat(after.get("isSoldOut").asBoolean()).isTrue();
        assertThat(slotAt(timeSlots(TUESDAY), "12:00:00").get("status").asText()).isEqualTo("SOLD_OUT");
    }

    @Test
    @DisplayName("Scenario 4: switching Stripe off removes it from the diner checkout (AC-3.1, INV-4)")
    void scenario4DisabledMethodIsHidden() throws Exception {
        togglePayment("STRIPE", false);

        mockMvc.perform(get(DINER + "/payment-methods").with(diner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].paymentMethod").value("MERCADO_PAGO"));
    }

    @Test
    @DisplayName("Scenario 5: with every method off the diner gets an empty list (EC-009, FR-013)")
    void scenario5AllMethodsDisabled() throws Exception {
        togglePayment("STRIPE", false);
        togglePayment("MERCADO_PAGO", false);

        mockMvc.perform(get(DINER + "/payment-methods").with(diner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Scenario 6: Stripe switched off while the diner is on screen is a 409 on confirm (INV-9, EC-010)")
    void scenario6DisabledMeanwhileIsRejectedOnConfirm() throws Exception {
        mockMvc.perform(get(DINER + "/payment-methods").with(diner()))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(post(DINER + "/checkout/validate-payment").with(diner())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"STRIPE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));

        togglePayment("STRIPE", false);

        mockMvc.perform(post(DINER + "/checkout/validate-payment").with(diner())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"STRIPE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Método de pago no disponible"))
                .andExpect(jsonPath("$.detail").value(
                        "El método de pago 'STRIPE' ha sido deshabilitado por el restaurante. Por favor seleccione otra opción."));
    }

    @Test
    @DisplayName("Scenario 7: two simultaneous approvals of the last place, exactly one wins and the count never reaches 51 (EC-016)")
    void scenario7ConcurrentApprovalOfTheLastPlace() throws Exception {
        configureSchedule("TUESDAY", "11:00:00", "16:00:00");
        configureCapacity(TUESDAY, "12:00:00", 50);
        clock.setBuenosAires(TUESDAY, LocalTime.of(10, 0));
        approveOrders(TUESDAY, LocalTime.of(12, 0), 49);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger approved = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    capacityService.consumeSlot(CAFE_ID, new TimeSlot(TUESDAY, LocalTime.of(12, 0)));
                    approved.incrementAndGet();
                } catch (SlotSoldOutException e) {
                    refused.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(approved.get()).isEqualTo(1);
        assertThat(refused.get()).isEqualTo(1);
        assertThat(slotOfMatrix(TUESDAY, "12:00:00").get("approvedOrdersCount").asInt()).isEqualTo(50);
    }

    private JsonNode slotOfMatrix(LocalDate date, String startTime) throws Exception {
        JsonNode matrix = json(mockMvc.perform(get(CAPACITIES).param("date", date.toString()).with(admin()))
                .andExpect(status().isOk()));
        for (JsonNode slot : matrix) {
            if (startTime.equals(slot.get("startTime").asText())) {
                return slot;
            }
        }
        throw new AssertionError("slot " + startTime + " not in the matrix of " + date);
    }

    private void assertThatApprovalIsRefused(LocalDate date, LocalTime start) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> capacityService.consumeSlot(CAFE_ID, new TimeSlot(date, start)))
                .isInstanceOf(SlotSoldOutException.class)
                .hasMessage("La franja seleccionada ha alcanzado su capacidad máxima permitida (50 pedidos).");
    }
}
