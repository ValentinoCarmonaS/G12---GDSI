import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
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

  describe('T022: Ergonomía visual de comanda táctil (US3 - CHF-3.2.1)', () => {
    it('debe visualizar cada plato en un renglón dedicado bajo el formato [Cantidad]x [Nombre] en Columna 2', () => {
      const ticketsWithMultipleDishes: KdsTicket[] = [
        {
          ticketId: 'ticket-multi-uuid',
          ticketNumber: 301,
          orderId: 'order-301-uuid',
          status: 'IN_PREPARATION',
          fulfillmentMode: 'IMMEDIATE',
          pickupDate: '2026-11-16',
          timeSlotStart: '13:00:00',
          timeSlotEnd: '13:15:00',
          queuedAt: '2026-11-16T16:00:00Z',
          startedAt: '2026-11-16T16:05:00Z',
          items: [
            { dishId: 'dish-1', dishName: 'Burger Deluxe', quantity: 2 },
            { dishId: 'dish-2', dishName: 'Papas Rústicas', quantity: 1 },
            { dishId: 'dish-3', dishName: 'Ensalada César', quantity: 3 },
          ],
        },
      ];

      render(
        <KitchenKanbanBoard
          tickets={ticketsWithMultipleDishes}
          onStartPreparation={vi.fn()}
        />
      );

      const inPrepCol = screen.getByTestId('in-preparation-column');
      expect(inPrepCol).toContainElement(screen.getByText('#301'));
      expect(screen.getByText('2x Burger Deluxe')).toBeInTheDocument();
      expect(screen.getByText('1x Papas Rústicas')).toBeInTheDocument();
      expect(screen.getByText('3x Ensalada César')).toBeInTheDocument();
    });

    it('no debe contener placeholders de notas vacías como "Sin notas" u "Observaciones: ninguna"', () => {
      render(
        <KitchenKanbanBoard
          tickets={mockTickets}
          onStartPreparation={vi.fn()}
        />
      );

      expect(screen.queryByText(/sin notas/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/observaciones/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/ninguna/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/modificaciones/i)).not.toBeInTheDocument();
    });

    it('debe exhibir contenedor con scroll táctil vertical (overflow-y-auto) en Columna 1 y Columna 2', () => {
      render(
        <KitchenKanbanBoard
          tickets={mockTickets}
          onStartPreparation={vi.fn()}
        />
      );

      const queuedCol = screen.getByTestId('queued-column');
      const inPrepCol = screen.getByTestId('in-preparation-column');

      const queuedScrollContainer = queuedCol.querySelector('.overflow-y-auto');
      const inPrepScrollContainer = inPrepCol.querySelector('.overflow-y-auto');

      expect(queuedScrollContainer).toBeInTheDocument();
      expect(inPrepScrollContainer).toBeInTheDocument();
    });

    it('debe mostrar mensajes neutrales de columna vacía sin alterar el layout fijo de 2 columnas', () => {
      render(
        <KitchenKanbanBoard
          tickets={[]}
          onStartPreparation={vi.fn()}
        />
      );

      expect(screen.getByText('Sin pedidos pendientes')).toBeInTheDocument();
      expect(screen.getByText('Sin pedidos en preparación')).toBeInTheDocument();

      const queuedCol = screen.getByTestId('queued-column');
      const inPrepCol = screen.getByTestId('in-preparation-column');
      expect(queuedCol).toBeInTheDocument();
      expect(inPrepCol).toBeInTheDocument();
    });
  });

  describe('Fase 6: Cronómetro progresivo de preparación Count-Up en Columna 2 (T029, US4, RF-011, RF-012)', () => {
    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date('2026-11-16T12:10:00.000Z').getTime());
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('debe renderizar el cronómetro con formato "MM min" en la cabecera de la tarjeta en preparación', () => {
      const ticketInPrep: KdsTicket = {
        ticketId: 'ticket-prep-10',
        ticketNumber: 401,
        orderId: 'order-401-uuid',
        fulfillmentMode: 'IMMEDIATE',
        status: 'IN_PREPARATION',
        pickupDate: '2026-11-16',
        timeSlotStart: '12:00:00',
        timeSlotEnd: '12:15:00',
        queuedAt: '2026-11-16T12:00:00.000Z',
        startedAt: '2026-11-16T12:03:00.000Z', // 7 minutos transcurridos a las 12:10:00
        items: [{ dishId: 'dish-1', dishName: 'Pizza Margarita', quantity: 1 }],
      };

      render(
        <KitchenKanbanBoard
          tickets={[ticketInPrep]}
          onStartPreparation={vi.fn()}
        />
      );

      const card = screen.getByTestId('in-preparation-card-ticket-prep-10');
      expect(card).toBeInTheDocument();
      expect(screen.getByText('7 min')).toBeInTheDocument();
    });
  });

  describe('Fase 7: Alerta visual por demora excesiva en KDS: umbral de 15 minutos (T031, US5, RF-013, RF-014)', () => {
    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date('2026-11-16T12:20:00.000Z').getTime());
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('debe mantener estilo neutral cuando tiempo < 15 minutos (14 min)', () => {
      const ticketNormal: KdsTicket = {
        ticketId: 'ticket-prep-14min',
        ticketNumber: 501,
        orderId: 'order-501-uuid',
        fulfillmentMode: 'IMMEDIATE',
        status: 'IN_PREPARATION',
        pickupDate: '2026-11-16',
        timeSlotStart: '12:00:00',
        timeSlotEnd: '12:15:00',
        queuedAt: '2026-11-16T12:00:00.000Z',
        startedAt: '2026-11-16T12:06:00.000Z', // 14 min transcurridos a las 12:20:00
        items: [{ dishId: 'dish-1', dishName: 'Hamburguesa Completa', quantity: 1 }],
      };

      render(
        <KitchenKanbanBoard
          tickets={[ticketNormal]}
          onStartPreparation={vi.fn()}
        />
      );

      const card = screen.getByTestId('in-preparation-card-ticket-prep-14min');
      const badge = screen.getByTestId('countup-badge-ticket-prep-14min');

      expect(card).not.toHaveClass('border-amber-500');
      expect(card).not.toHaveClass('ring-2');
      expect(badge).not.toHaveClass('bg-amber-100');
    });

    it('debe conmutar borde exterior e insignia a ámbar cuando tiempo >= 15 minutos, manteniendo platos neutros', () => {
      const ticketDelayed: KdsTicket = {
        ticketId: 'ticket-prep-15min',
        ticketNumber: 502,
        orderId: 'order-502-uuid',
        fulfillmentMode: 'IMMEDIATE',
        status: 'IN_PREPARATION',
        pickupDate: '2026-11-16',
        timeSlotStart: '12:00:00',
        timeSlotEnd: '12:15:00',
        queuedAt: '2026-11-16T12:00:00.000Z',
        startedAt: '2026-11-16T12:05:00.000Z', // 15 min exactos a las 12:20:00
        items: [{ dishId: 'dish-1', dishName: 'Bife de Chorizo', quantity: 2 }],
      };

      render(
        <KitchenKanbanBoard
          tickets={[ticketDelayed]}
          onStartPreparation={vi.fn()}
        />
      );

      const card = screen.getByTestId('in-preparation-card-ticket-prep-15min');
      const badge = screen.getByTestId('countup-badge-ticket-prep-15min');

      // Alerta visual perimetral e insignia
      expect(card).toHaveClass('border-amber-500');
      expect(card).toHaveClass('ring-2');
      expect(badge).toHaveClass('bg-amber-100');
      expect(badge).toHaveClass('text-amber-800');

      // Verificación de fondo e ítems neutros (legibilidad sin teñir textos de platos)
      const dishItem = screen.getByText('2x Bife de Chorizo');
      expect(dishItem).toHaveClass('text-slate-100');
      expect(dishItem).not.toHaveClass('text-amber-500');
      expect(dishItem).not.toHaveClass('text-amber-800');
    });
  });
});
