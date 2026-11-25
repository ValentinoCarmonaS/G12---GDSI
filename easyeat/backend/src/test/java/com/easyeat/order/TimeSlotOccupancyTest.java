package com.easyeat.order;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.dto.PickupSlotCatalogResponse;
import com.easyeat.order.dto.TimeSlotAvailability;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.TimeSlotOccupancyProjection;
import com.easyeat.order.service.PickupSlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeSlotOccupancyTest {

    @Mock
    private EstablishmentRepository establishmentRepository;

    @Mock
    private OrderRepository orderRepository;

    private Clock fixedClock;
    private PickupSlotService pickupSlotService;
    private UUID establishmentId;
    private Establishment testEstablishment;

    private final ZoneId zoneId = ZoneId.of("America/Argentina/Buenos_Aires");
    // 2026-11-03 08:00:00 (Well before opening at 12:00)
    private final Instant testInstant = Instant.parse("2026-11-03T11:00:00Z");

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(testInstant, zoneId);
        pickupSlotService = new PickupSlotService(establishmentRepository, orderRepository, fixedClock);
        establishmentId = UUID.randomUUID();

        testEstablishment = new Establishment();
        testEstablishment.setId(establishmentId);
        testEstablishment.setName("Restaurante Central");
        testEstablishment.setOpeningTime(LocalTime.of(12, 0));
        testEstablishment.setClosingTime(LocalTime.of(13, 0)); // 4 slots: 12:00, 12:15, 12:30, 12:45
        testEstablishment.setMaxCapacityPerSlot(10);
        testEstablishment.setLeadTimeMinutes(30);
        testEstablishment.setTimezone("America/Argentina/Buenos_Aires");
        testEstablishment.setOperatingDays("TUESDAY,WEDNESDAY");

        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(testEstablishment));
    }

    private TimeSlotOccupancyProjection slotCount(LocalTime start, long count) {
        return new TimeSlotOccupancyProjection() {
            @Override
            public LocalTime getTimeSlotStart() {
                return start;
            }

            @Override
            public long getBookedCount() {
                return count;
            }
        };
    }

    @Test
    @DisplayName("Occupancy math computes percentage accurately: booked / maxCapacity * 100")
    void shouldComputeOccupancyPercentageAccurately() {
        LocalDate testDate = LocalDate.of(2026, 11, 3); // Tuesday

        when(orderRepository.countActiveOrdersGroupedBySlot(eq(establishmentId), eq(testDate), any(Instant.class)))
                .thenReturn(List.of(
                        slotCount(LocalTime.of(12, 0), 5L),
                        slotCount(LocalTime.of(12, 30), 7L),
                        slotCount(LocalTime.of(12, 45), 3L)
                ));

        PickupSlotCatalogResponse response = pickupSlotService.getPickupSlots(establishmentId, testDate);

        verify(orderRepository, times(1)).countActiveOrdersGroupedBySlot(eq(establishmentId), eq(testDate), any(Instant.class));
        verify(orderRepository, never()).countActiveOrdersBySlot(any(), any(), any(), any());

        assertThat(response.getSlots()).hasSize(4);
        TimeSlotAvailability slot0 = response.getSlots().get(0);
        assertThat(slot0.getBookedCount()).isEqualTo(5L);
        assertThat(slot0.getMaxCapacity()).isEqualTo(10);
        assertThat(slot0.getOccupancyPercentage()).isEqualTo(50.0);
        assertThat(response.getSlots().get(1).getBookedCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Saturation tier: occupancy < 80% yields AVAILABLE (NORMAL) status and isSelectable=true")
    void shouldMarkSlotAsAvailableWhenOccupancyBelow80Percent() {
        LocalDate testDate = LocalDate.of(2026, 11, 3);

        when(orderRepository.countActiveOrdersGroupedBySlot(eq(establishmentId), eq(testDate), any(Instant.class)))
                .thenReturn(List.of(slotCount(LocalTime.of(12, 0), 7L))); // 7/10 = 70% (<80%)

        PickupSlotCatalogResponse response = pickupSlotService.getPickupSlots(establishmentId, testDate);

        verify(orderRepository, times(1)).countActiveOrdersGroupedBySlot(eq(establishmentId), eq(testDate), any(Instant.class));
        verify(orderRepository, never()).countActiveOrdersBySlot(any(), any(), any(), any());

        TimeSlotAvailability slot = response.getSlots().get(0);
        assertThat(slot.getStatus()).isEqualTo("AVAILABLE");
        assertThat(slot.isSelectable()).isTrue();
        assertThat(slot.getOccupancyPercentage()).isEqualTo(70.0);
    }

    @Test
    @DisplayName("Saturation tier: occupancy between 80% and 99% yields HIGH_DEMAND status and isSelectable=true")
    void shouldMarkSlotAsHighDemandWhenOccupancyBetween80And99Percent() {
        LocalDate testDate = LocalDate.of(2026, 11, 3);

        when(orderRepository.countActiveOrdersGroupedBySlot(eq(establishmentId), eq(testDate), any(Instant.class)))
                .thenReturn(List.of(
                        slotCount(LocalTime.of(12, 0), 8L), // 8/10 = 80.0%
                        slotCount(LocalTime.of(12, 15), 9L) // 9/10 = 90.0%
                ));

        PickupSlotCatalogResponse response = pickupSlotService.getPickupSlots(establishmentId, testDate);

        verify(orderRepository, times(1)).countActiveOrdersGroupedBySlot(eq(establishmentId), eq(testDate), any(Instant.class));
        verify(orderRepository, never()).countActiveOrdersBySlot(any(), any(), any(), any());

        TimeSlotAvailability slot80 = response.getSlots().get(0);
        assertThat(slot80.getStatus()).isEqualTo("HIGH_DEMAND");
        assertThat(slot80.isSelectable()).isTrue();
        assertThat(slot80.getOccupancyPercentage()).isEqualTo(80.0);

        TimeSlotAvailability slot90 = response.getSlots().get(1);
        assertThat(slot90.getStatus()).isEqualTo("HIGH_DEMAND");
        assertThat(slot90.isSelectable()).isTrue();
        assertThat(slot90.getOccupancyPercentage()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("Saturation tier: occupancy >= 100% yields SOLD_OUT and filters out the slot (C-12)")
    void shouldFilterSoldOutSlotsWhenOccupancyReaches100Percent() {
        LocalDate testDate = LocalDate.of(2026, 11, 3);

        when(orderRepository.countActiveOrdersGroupedBySlot(eq(establishmentId), eq(testDate), any(Instant.class)))
                .thenReturn(List.of(
                        slotCount(LocalTime.of(12, 0), 10L),  // 10/10 = 100.0% (SOLD_OUT -> filtered out)
                        slotCount(LocalTime.of(12, 15), 12L), // 12/10 = 120.0% (over capacity -> filtered out)
                        slotCount(LocalTime.of(12, 30), 4L),  // 4/10 = 40.0% (AVAILABLE -> retained)
                        slotCount(LocalTime.of(12, 45), 8L)   // 8/10 = 80.0% (HIGH_DEMAND -> retained)
                ));

        PickupSlotCatalogResponse response = pickupSlotService.getPickupSlots(establishmentId, testDate);

        verify(orderRepository, times(1)).countActiveOrdersGroupedBySlot(eq(establishmentId), eq(testDate), any(Instant.class));
        verify(orderRepository, never()).countActiveOrdersBySlot(any(), any(), any(), any());

        // Slots at 12:00 and 12:15 are filtered out. Only 12:30 and 12:45 remain.
        assertThat(response.getSlots()).hasSize(2);

        TimeSlotAvailability slot30 = response.getSlots().get(0);
        assertThat(slot30.getStartTime()).isEqualTo(LocalTime.of(12, 30));
        assertThat(slot30.getStatus()).isEqualTo("AVAILABLE");
        assertThat(slot30.isSelectable()).isTrue();

        TimeSlotAvailability slot45 = response.getSlots().get(1);
        assertThat(slot45.getStartTime()).isEqualTo(LocalTime.of(12, 45));
        assertThat(slot45.getStatus()).isEqualTo("HIGH_DEMAND");
        assertThat(slot45.isSelectable()).isTrue();
    }
}
