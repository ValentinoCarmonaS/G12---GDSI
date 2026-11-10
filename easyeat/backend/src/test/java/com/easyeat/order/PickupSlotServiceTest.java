package com.easyeat.order;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.PickupSlotCatalogResponse;
import com.easyeat.order.dto.TimeSlotAvailability;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.service.PickupSlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PickupSlotServiceTest {

    @Mock
    private EstablishmentRepository establishmentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private com.easyeat.operations.repository.OperatingScheduleRepository operatingScheduleRepository;

    private Clock clock;
    private PickupSlotService pickupSlotService;

    private UUID establishmentId;
    private Establishment establishment;
    private ZoneId buenosAiresZone;
    private Instant fixedInstant;

    @BeforeEach
    void setUp() {
        buenosAiresZone = ZoneId.of("America/Argentina/Buenos_Aires");
        // Fixed at Monday 2026-11-02 12:00:00 (Buenos Aires time)
        LocalDateTime localDateTime = LocalDateTime.of(2026, 11, 2, 12, 0, 0);
        fixedInstant = localDateTime.atZone(buenosAiresZone).toInstant();
        clock = Clock.fixed(fixedInstant, buenosAiresZone);
        pickupSlotService = new PickupSlotService(establishmentRepository, orderRepository, clock, operatingScheduleRepository);

        establishmentId = UUID.randomUUID();
        establishment = new Establishment();
        establishment.setId(establishmentId);
        establishment.setName("Parrilla Don Julio");
        establishment.setStatus(EstablishmentStatus.ACTIVE);
        establishment.setOpeningTime(LocalTime.of(11, 0));
        establishment.setClosingTime(LocalTime.of(15, 0)); // 11:00 to 15:00 = 16 15-min slots
        establishment.setTimezone("America/Argentina/Buenos_Aires");
        establishment.setLeadTimeMinutes(30);
        establishment.setMaxCapacityPerSlot(10);
        establishment.setOperatingDays("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY");
    }

    @Nested
    @DisplayName("15-Minute Continuous Slot Projection")
    class SlotProjectionTests {

        @Test
        @DisplayName("Should generate continuous 15-minute intervals between opening and closing time")
        void shouldGenerateContinuousFifteenMinuteIntervals() {
            when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate targetDate = LocalDate.of(2026, 11, 3); // Tuesday (future operating day)
            PickupSlotCatalogResponse response = pickupSlotService.getPickupSlots(establishmentId, targetDate);

            assertThat(response).isNotNull();
            assertThat(response.getEstablishmentId()).isEqualTo(establishmentId);
            assertThat(response.getDate()).isEqualTo(targetDate);
            assertThat(response.isOperatingDay()).isTrue();

            List<TimeSlotAvailability> slots = response.getSlots();
            // From 11:00 to 15:00 is 4 hours = 16 slots of 15 min
            assertThat(slots).hasSize(16);

            // First slot: 11:00 to 11:15
            assertThat(slots.get(0).getStartTime()).isEqualTo(LocalTime.of(11, 0));
            assertThat(slots.get(0).getEndTime()).isEqualTo(LocalTime.of(11, 15));

            // Last slot: 14:45 to 15:00
            assertThat(slots.get(15).getStartTime()).isEqualTo(LocalTime.of(14, 45));
            assertThat(slots.get(15).getEndTime()).isEqualTo(LocalTime.of(15, 0));

            // Verify continuous step of 15 min
            for (int i = 0; i < slots.size() - 1; i++) {
                assertThat(slots.get(i).getEndTime()).isEqualTo(slots.get(i + 1).getStartTime());
                assertThat(Duration.between(slots.get(i).getStartTime(), slots.get(i).getEndTime()).toMinutes()).isEqualTo(15);
            }
        }
    }

    @Nested
    @DisplayName("7 Operating Days Window Calculation")
    class OperatingDaysWindowTests {

        @Test
        @DisplayName("Should throw exception when target date is beyond 7 days from today")
        void shouldRejectDateBeyondOperatingWindow() {
            when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate outOfBoundsDate = LocalDate.of(2026, 11, 10); // 8 days later

            assertThatThrownBy(() -> pickupSlotService.getPickupSlots(establishmentId, outOfBoundsDate))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("ventana operativa");
        }

        @Test
        @DisplayName("Should throw exception when target date is before today")
        void shouldRejectPastDate() {
            when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate pastDate = LocalDate.of(2026, 11, 1); // Yesterday

            assertThatThrownBy(() -> pickupSlotService.getPickupSlots(establishmentId, pastDate))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("pasada");
        }
    }

    @Nested
    @DisplayName("Closed / Non-Operating Days Handling")
    class NonOperatingDaysTests {

        @Test
        @DisplayName("Should return empty slots list when establishment is closed on that day")
        void shouldReturnEmptySlotsForClosedDay() {
            when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate saturday = LocalDate.of(2026, 11, 7); // Saturday (closed per operatingDays)
            PickupSlotCatalogResponse response = pickupSlotService.getPickupSlots(establishmentId, saturday);

            assertThat(response).isNotNull();
            assertThat(response.isOperatingDay()).isFalse();
            assertThat(response.getSlots()).isEmpty();
        }
    }

    @Nested
    @DisplayName("30-Minute Lead Time Buffer Enforcement and Filtering (C-12)")
    class LeadTimeBufferTests {

        @Test
        @DisplayName("Should filter out slots in the past or within 30 minutes of server current time (C-12)")
        void shouldFilterPastAndLeadTimeSlots() {
            // Fixed time is 12:00:00 on Monday 2026-11-02
            // Lead time is 30 minutes -> cutoff is 12:30:00
            when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate today = LocalDate.of(2026, 11, 2);
            PickupSlotCatalogResponse response = pickupSlotService.getPickupSlots(establishmentId, today);

            List<TimeSlotAvailability> slots = response.getSlots();

            // Total slots was 16. The 6 slots before 12:30 (11:00 to 12:30) must be omitted.
            // Remaining 10 slots (12:30 to 15:00) must all be selectable.
            assertThat(slots).hasSize(10);
            for (TimeSlotAvailability slot : slots) {
                assertThat(slot.getStartTime()).isAfterOrEqualTo(LocalTime.of(12, 30));
                assertThat(slot.isSelectable()).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("C-12: Operating Schedule (010) Cross-check and Strict Filtering")
    class OperatingScheduleCrossCheckTests {

        @Test
        @DisplayName("Should return empty slots when day is marked closed in establishment_operating_schedules")
        void shouldReturnEmptySlotsWhenScheduleIsClosed() {
            when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate tuesday = LocalDate.of(2026, 11, 3);
            com.easyeat.operations.domain.EstablishmentOperatingSchedule closedSched =
                    new com.easyeat.operations.domain.EstablishmentOperatingSchedule(
                            establishmentId, DayOfWeek.TUESDAY, LocalTime.of(11, 0), LocalTime.of(15, 0), true
                    );
            when(operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(establishmentId, DayOfWeek.TUESDAY))
                    .thenReturn(Optional.of(closedSched));

            PickupSlotCatalogResponse response = pickupSlotService.getPickupSlots(establishmentId, tuesday);

            assertThat(response).isNotNull();
            assertThat(response.isOperatingDay()).isFalse();
            assertThat(response.getSlots()).isEmpty();
        }

        @Test
        @DisplayName("Should strictly constrain slots to custom opening and closing hours from establishment_operating_schedules")
        void shouldConstrainSlotsToCustomOperatingHours() {
            when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate wednesday = LocalDate.of(2026, 11, 4);
            // Custom window: 12:00 to 13:30 (6 15-minute slots: 12:00-12:15, 12:15-12:30, 12:30-12:45, 12:45-13:00, 13:00-13:15, 13:15-13:30)
            com.easyeat.operations.domain.EstablishmentOperatingSchedule customSched =
                    new com.easyeat.operations.domain.EstablishmentOperatingSchedule(
                            establishmentId, DayOfWeek.WEDNESDAY, LocalTime.of(12, 0), LocalTime.of(13, 30), false
                    );
            when(operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(establishmentId, DayOfWeek.WEDNESDAY))
                    .thenReturn(Optional.of(customSched));

            PickupSlotCatalogResponse response = pickupSlotService.getPickupSlots(establishmentId, wednesday);

            assertThat(response).isNotNull();
            assertThat(response.isOperatingDay()).isTrue();
            assertThat(response.getSlots()).hasSize(6);

            assertThat(response.getSlots().get(0).getStartTime()).isEqualTo(LocalTime.of(12, 0));
            assertThat(response.getSlots().get(5).getEndTime()).isEqualTo(LocalTime.of(13, 30));
        }
    }
}

