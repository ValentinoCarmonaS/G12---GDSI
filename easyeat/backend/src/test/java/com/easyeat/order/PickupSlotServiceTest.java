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
    private Clock clock;

    @InjectMocks
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
            when(clock.instant()).thenReturn(fixedInstant);
            when(clock.getZone()).thenReturn(buenosAiresZone);
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
            when(clock.instant()).thenReturn(fixedInstant);
            when(clock.getZone()).thenReturn(buenosAiresZone);
            when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate outOfBoundsDate = LocalDate.of(2026, 11, 10); // 8 days later

            assertThatThrownBy(() -> pickupSlotService.getPickupSlots(establishmentId, outOfBoundsDate))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("ventana operativa");
        }

        @Test
        @DisplayName("Should throw exception when target date is before today")
        void shouldRejectPastDate() {
            when(clock.instant()).thenReturn(fixedInstant);
            when(clock.getZone()).thenReturn(buenosAiresZone);
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
            when(clock.instant()).thenReturn(fixedInstant);
            when(clock.getZone()).thenReturn(buenosAiresZone);
            when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate saturday = LocalDate.of(2026, 11, 7); // Saturday (closed per operatingDays)
            PickupSlotCatalogResponse response = pickupSlotService.getPickupSlots(establishmentId, saturday);

            assertThat(response).isNotNull();
            assertThat(response.isOperatingDay()).isFalse();
            assertThat(response.getSlots()).isEmpty();
        }
    }

    @Nested
    @DisplayName("30-Minute Lead Time Buffer Enforcement")
    class LeadTimeBufferTests {

        @Test
        @DisplayName("Should disable slots in the past or within 30 minutes of server current time")
        void shouldMarkPastAndLeadTimeSlotsAsNonSelectable() {
            // Fixed time is 12:00:00 on Monday 2026-11-02
            // Lead time is 30 minutes -> cutoff is 12:30:00
            when(clock.instant()).thenReturn(fixedInstant);
            when(clock.getZone()).thenReturn(buenosAiresZone);
            when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate today = LocalDate.of(2026, 11, 2);
            PickupSlotCatalogResponse response = pickupSlotService.getPickupSlots(establishmentId, today);

            List<TimeSlotAvailability> slots = response.getSlots();

            // Slots before 12:30 should NOT be selectable (11:00-11:15, 11:15-11:30, 11:30-11:45, 11:45-12:00, 12:00-12:15, 12:15-12:30)
            // 6 slots disabled
            for (TimeSlotAvailability slot : slots) {
                if (slot.getStartTime().isBefore(LocalTime.of(12, 30))) {
                    assertThat(slot.isSelectable())
                            .as("Slot starting at " + slot.getStartTime() + " must be non-selectable due to lead time buffer")
                            .isFalse();
                } else {
                    assertThat(slot.isSelectable())
                            .as("Slot starting at " + slot.getStartTime() + " must be selectable")
                            .isTrue();
                }
            }
        }
    }
}
