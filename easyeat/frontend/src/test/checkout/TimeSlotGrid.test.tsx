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

  it('renders time slot chips with formatted times', () => {
    const handleSelect = vi.fn();
    render(
      <TimeSlotGrid
        slots={mockSlots}
        selectedSlot={null}
        onSelectSlot={handleSelect}
      />
    );

    expect(screen.getByText('12:00 - 12:15')).toBeInTheDocument();
    expect(screen.getByText('12:30 - 12:45')).toBeInTheDocument();
    expect(screen.getByText('12:45 - 13:00')).toBeInTheDocument();
    expect(screen.getByText('13:00 - 13:15')).toBeInTheDocument();
  });

  it('disables slots that are not selectable due to lead-time or sold out', () => {
    const handleSelect = vi.fn();
    render(
      <TimeSlotGrid
        slots={mockSlots}
        selectedSlot={null}
        onSelectSlot={handleSelect}
      />
    );

    const leadTimeButton = screen.getByText('12:00 - 12:15').closest('button');
    expect(leadTimeButton).toBeDisabled();

    const soldOutButton = screen.getByText('13:00 - 13:15').closest('button');
    expect(soldOutButton).toBeDisabled();

    const selectableButton = screen.getByText('12:30 - 12:45').closest('button');
    expect(selectableButton).not.toBeDisabled();
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
    const { rerender } = render(
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
