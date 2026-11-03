import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { CheckoutSummary } from '../../components/checkout/CheckoutSummary';
import { CartItem } from '../../types/cart';
import { TimeSlotAvailability } from '../../types/order';

describe('CheckoutSummary Component', () => {
  const mockItems: CartItem[] = [
    {
      dishId: 'dish-1',
      name: 'Hamburguesa Completa',
      price: 1500.5,
      quantity: 2,
    },
    {
      dishId: 'dish-2',
      name: 'Papas Fritas',
      price: 800,
      quantity: 1,
    },
  ];

  const normalSlot: TimeSlotAvailability = {
    startTime: '12:30:00',
    endTime: '12:45:00',
    date: '2026-11-03',
    maxCapacity: 10,
    bookedCount: 5,
    occupancyPercentage: 50,
    status: 'AVAILABLE',
    isSelectable: true,
  };

  const highDemandSlot: TimeSlotAvailability = {
    startTime: '12:45:00',
    endTime: '13:00:00',
    date: '2026-11-03',
    maxCapacity: 10,
    bookedCount: 8,
    occupancyPercentage: 80,
    status: 'HIGH_DEMAND',
    isSelectable: true,
  };

  it('renders order items and calculates formatted total price with 2 decimals', () => {
    render(
      <CheckoutSummary
        items={mockItems}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByText('Hamburguesa Completa')).toBeInTheDocument();
    expect(screen.getByText('2x')).toBeInTheDocument();
    expect(screen.getByText('Papas Fritas')).toBeInTheDocument();
    expect(screen.getByText('1x')).toBeInTheDocument();

    // Total: (1500.5 * 2) + (800 * 1) = 3001 + 800 = 3801.00
    expect(screen.getByText('$3801.00')).toBeInTheDocument();
  });

  it('renders immediate mode details correctly', () => {
    render(
      <CheckoutSummary
        items={mockItems}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByText('Lo antes posible (Inmediato)')).toBeInTheDocument();
  });

  it('renders scheduled mode with date and slot details', () => {
    render(
      <CheckoutSummary
        items={mockItems}
        fulfillmentMode="SCHEDULED"
        selectedDate="2026-11-03"
        selectedSlot={normalSlot}
      />
    );

    expect(screen.getByText('Para más tarde (Turno programado)')).toBeInTheDocument();
    expect(screen.getByText('03/11/2026')).toBeInTheDocument();
    expect(screen.getByText('12:30 - 12:45 hs')).toBeInTheDocument();
    expect(
      screen.queryByText(/Esta franja registra un alto volumen de cocina/i)
    ).not.toBeInTheDocument();
  });

  it('displays non-blocking preventive amber alert when a high demand slot is selected', () => {
    render(
      <CheckoutSummary
        items={mockItems}
        fulfillmentMode="SCHEDULED"
        selectedDate="2026-11-03"
        selectedSlot={highDemandSlot}
      />
    );

    const alert = screen.getByRole('alert');
    expect(alert).toBeInTheDocument();
    expect(
      screen.getByText(
        'Esta franja registra un alto volumen de cocina. Podrían producirse leves demoras en mostrador.'
      )
    ).toBeInTheDocument();
  });
});
