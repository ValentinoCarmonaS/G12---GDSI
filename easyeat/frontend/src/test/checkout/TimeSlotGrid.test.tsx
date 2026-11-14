import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { TimeSlotGrid } from '../../components/checkout/TimeSlotGrid';
import { TimeSlotAvailability } from '../../types/order';

describe('TimeSlotGrid Component', () => {
  const mockSlots: TimeSlotAvailability[] = [
    {
      startTime: '12:00:00',
      endTime: '12:15:00',
      date: '2026-11-02',
      maxCapacity: 10,
      bookedCount: 0,
      occupancyPercentage: 0,
      status: 'AVAILABLE',
      isSelectable: false, // Disabled due to lead-time or past
    },
    {
      startTime: '12:15:00',
      endTime: '12:30:00',
      date: '2026-11-02',
      maxCapacity: 10,
      bookedCount: 0,
      occupancyPercentage: 0,
      status: 'AVAILABLE',
      isSelectable: false, // Disabled
    },
    {
      startTime: '12:30:00',
      endTime: '12:45:00',
      date: '2026-11-02',
      maxCapacity: 10,
      bookedCount: 2,
      occupancyPercentage: 20,
      status: 'AVAILABLE',
      isSelectable: true,
    },
    {
      startTime: '12:45:00',
      endTime: '13:00:00',
      date: '2026-11-02',
      maxCapacity: 10,
      bookedCount: 8,
      occupancyPercentage: 80,
      status: 'HIGH_DEMAND',
      isSelectable: true,
    },
    {
      startTime: '13:00:00',
      endTime: '13:15:00',
      date: '2026-11-02',
      maxCapacity: 10,
      bookedCount: 10,
      occupancyPercentage: 100,
      status: 'SOLD_OUT',
      isSelectable: false,
    },
  ];

  it('renders active time slot chips with formatted times and omits disabled or sold out slots (C-12)', () => {
    const handleSelect = vi.fn();
    render(
      <TimeSlotGrid
        slots={mockSlots}
        selectedSlot={null}
        onSelectSlot={handleSelect}
      />
    );

    // Active slots are rendered
    expect(screen.getByText('12:30 - 12:45')).toBeInTheDocument();
    expect(screen.getByText('12:45 - 13:00')).toBeInTheDocument();

    // Expired or sold out slots are omitted from the UI (C-12)
    expect(screen.queryByText('12:00 - 12:15')).not.toBeInTheDocument();
    expect(screen.queryByText('13:00 - 13:15')).not.toBeInTheDocument();
  });

  it('hides slots that are not selectable due to lead-time or sold out (C-12)', () => {
    const handleSelect = vi.fn();
    render(
      <TimeSlotGrid
        slots={mockSlots}
        selectedSlot={null}
        onSelectSlot={handleSelect}
      />
    );

    // Slots with lead-time violation or sold-out must not be in the DOM
    expect(screen.queryByText('12:00 - 12:15')).toBeNull();
    expect(screen.queryByText('13:00 - 13:15')).toBeNull();

    const selectableButton = screen.getByText('12:30 - 12:45').closest('button');
    expect(selectableButton).not.toBeDisabled();
  });

  it('renders amber styling and Alta Demanda badge for 80-99% saturation', () => {
    const handleSelect = vi.fn();
    render(
      <TimeSlotGrid
        slots={mockSlots}
        selectedSlot={null}
        onSelectSlot={handleSelect}
      />
    );

    const highDemandBadge = screen.getByText('Alta Demanda');
    expect(highDemandBadge).toBeInTheDocument();

    const highDemandButton = screen.getByText('12:45 - 13:00').closest('button');
    expect(highDemandButton).not.toBeDisabled();
    expect(highDemandButton?.className).toContain('border-amber-300');
  });

  it('displays informative empty state when all slots are sold out or unavailable (C-12 / FR-035)', () => {
    const soldOutSlots: TimeSlotAvailability[] = [
      {
        startTime: '13:00:00',
        endTime: '13:15:00',
        date: '2026-11-02',
        maxCapacity: 10,
        bookedCount: 10,
        occupancyPercentage: 100,
        status: 'SOLD_OUT',
        isSelectable: false,
      },
    ];

    const handleSelect = vi.fn();
    render(
      <TimeSlotGrid
        slots={soldOutSlots}
        selectedSlot={null}
        onSelectSlot={handleSelect}
      />
    );

    expect(
      screen.getByText('No hay horarios de retiro disponibles para el día seleccionado.')
    ).toBeInTheDocument();
  });

  it('triggers onSelectSlot callback when an available slot is clicked', () => {
    const handleSelect = vi.fn();
    render(
      <TimeSlotGrid
        slots={mockSlots}
        selectedSlot={null}
        onSelectSlot={handleSelect}
      />
    );

    const selectableButton = screen.getByText('12:30 - 12:45').closest('button');
    expect(selectableButton).not.toBeNull();
    fireEvent.click(selectableButton!);

    expect(handleSelect).toHaveBeenCalledTimes(1);
    expect(handleSelect).toHaveBeenCalledWith(mockSlots[2]);
  });

  it('highlights the selected slot chip', () => {
    const handleSelect = vi.fn();
    render(
      <TimeSlotGrid
        slots={mockSlots}
        selectedSlot={mockSlots[2]}
        onSelectSlot={handleSelect}
      />
    );

    const selectedButton = screen.getByText('12:30 - 12:45').closest('button');
    expect(selectedButton).toHaveAttribute('aria-selected', 'true');
  });
});
