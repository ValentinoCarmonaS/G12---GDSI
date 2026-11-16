import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { KitchenKanbanBoard } from '../../src/components/kds/KitchenKanbanBoard';
import type { KdsTicket } from '../../src/types/kds';

describe('T016: KitchenKanbanBoard Component - Start Preparation (US2)', () => {
  const mockTickets: KdsTicket[] = [
    {
      ticketId: 'ticket-101-uuid',
      ticketNumber: 101,
      orderId: 'order-101-uuid',
      status: 'QUEUED',
      fulfillmentMode: 'IMMEDIATE',
      pickupDate: '2026-11-16',
      timeSlotStart: '12:00:00',
      timeSlotEnd: '12:15:00',
      queuedAt: '2026-11-16T15:00:00Z',
      startedAt: null,
      items: [
        { dishId: 'dish-1', dishName: 'Burger Deluxe', quantity: 2 },
      ],
    },
    {
      ticketId: 'ticket-102-uuid',
      ticketNumber: 102,
      orderId: 'order-102-uuid',
      status: 'IN_PREPARATION',
      fulfillmentMode: 'SCHEDULED',
      pickupDate: '2026-11-16',
      timeSlotStart: '12:15:00',
      timeSlotEnd: '12:30:00',
      queuedAt: '2026-11-16T15:05:00Z',
      startedAt: '2026-11-16T15:10:00Z',
      items: [
        { dishId: 'dish-2', dishName: 'Papas Rústicas', quantity: 1 },
      ],
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('debe renderizar el botón "EMPEZAR" en la comanda de Columna 1', () => {
    render(
      <KitchenKanbanBoard
        tickets={mockTickets}
        onStartPreparation={vi.fn()}
      />
    );

    const startButton = screen.getByRole('button', { name: /empezar/i });
    expect(startButton).toBeInTheDocument();
    expect(startButton).not.toBeDisabled();
    expect(screen.getByText('#101')).toBeInTheDocument();
  });

  it('debe deshabilitar el botón "EMPEZAR" durante la mutación para evitar doble pulsación', async () => {
    let resolveMutation: (val?: unknown) => void = () => {};
    const onStartPreparationMock = vi.fn().mockImplementation(() => {
      return new Promise((resolve) => {
        resolveMutation = resolve;
      });
    });

    render(
      <KitchenKanbanBoard
        tickets={mockTickets}
        onStartPreparation={onStartPreparationMock}
      />
    );

    const startButton = screen.getByRole('button', { name: /empezar/i });
    fireEvent.click(startButton);

    expect(onStartPreparationMock).toHaveBeenCalledTimes(1);
    expect(onStartPreparationMock).toHaveBeenCalledWith('ticket-101-uuid');
    expect(startButton).toBeDisabled();

    resolveMutation();
    await waitFor(() => {
      // una vez finalizado o re-renderizado
    });
  });

  it('debe trasladar reactivamente la tarjeta a Columna 2 cuando cambia el estado', () => {
    const { rerender } = render(
      <KitchenKanbanBoard
        tickets={mockTickets}
        onStartPreparation={vi.fn()}
      />
    );

    expect(screen.getByTestId('queued-column')).toContainElement(screen.getByText('#101'));
    expect(screen.getByTestId('in-preparation-column')).toContainElement(screen.getByText('#102'));

    // Simulamos actualización reactiva tras mutación exitosa
    const updatedTickets: KdsTicket[] = [
      {
        ...mockTickets[0],
        status: 'IN_PREPARATION',
        startedAt: '2026-11-16T15:20:00Z',
      },
      mockTickets[1],
    ];

    rerender(
      <KitchenKanbanBoard
        tickets={updatedTickets}
        onStartPreparation={vi.fn()}
      />
    );

    expect(screen.getByTestId('in-preparation-column')).toContainElement(screen.getByText('#101'));
    expect(screen.getByTestId('in-preparation-column')).toContainElement(screen.getByText('#102'));
  });
});
