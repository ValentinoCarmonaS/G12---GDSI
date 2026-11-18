import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CounterTicketCard } from '../../src/components/counter/CounterTicketCard';
import type { CounterReadyOrder } from '../../src/types/counter';

const sampleOrder: CounterReadyOrder = {
  ticketId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
  orderId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
  ticketNumber: 104,
  timeSlot: {
    start: '13:00',
    end: '13:15',
  },
  readyAt: '2026-11-15T19:30:00.000Z',
  customer: {
    userId: 'c71e2289-4b68-45e0-a7d1-e6e7d6928e01',
    fullName: 'Valentino Carmona',
    photoUrl: '/api/v1/profile/users/c71e2289-4b68-45e0-a7d1-e6e7d6928e01/avatar',
  },
  securityCode: '4821',
  items: [
    {
      quantity: 2,
      dishName: 'Hamburguesa Completa con Papas',
    },
    {
      quantity: 1,
      dishName: 'Limonada con Menta y Jengibre',
    },
  ],
};

describe('CounterTicketCard (T021, US3, ENC-2.1.1, RF-006..RF-011)', () => {
  const readyAtMs = Date.parse(sampleOrder.readyAt);

  it('T021: renders compact closed state with #ticketNumber, timeSlot "HH:mm - HH:mm" and wait counter in minutes', () => {
    const onToggle = vi.fn();
    render(
      <CounterTicketCard
        order={sampleOrder}
        isExpanded={false}
        onToggle={onToggle}
        nowMs={readyAtMs + 30_000}
      />
    );

    expect(screen.getByText('#104')).toBeInTheDocument();
    expect(screen.getByText('13:00 - 13:15')).toBeInTheDocument();
    expect(screen.getByText('0 min')).toBeInTheDocument();
    expect(
      screen.queryByTestId('counter-card-expanded-body')
    ).not.toBeInTheDocument();
  });

  it('T021: toggles expansion when clicking the card header', async () => {
    const user = userEvent.setup();
    const onToggle = vi.fn();

    render(
      <CounterTicketCard
        order={sampleOrder}
        isExpanded={false}
        onToggle={onToggle}
        nowMs={readyAtMs + 5 * 60_000}
      />
    );

    expect(screen.getByText('5 min')).toBeInTheDocument();

    const header = screen.getByTestId('counter-card-header');
    await user.click(header);

    expect(onToggle).toHaveBeenCalledTimes(1);
    expect(onToggle).toHaveBeenCalledWith(sampleOrder.ticketId);
  });

  it('T021: stops propagation on clicks inside expanded body (items list and delivery button container) while allowing header click to collapse', async () => {
    const user = userEvent.setup();
    const onToggle = vi.fn();

    render(
      <CounterTicketCard
        order={sampleOrder}
        isExpanded={true}
        onToggle={onToggle}
        nowMs={readyAtMs + 2 * 60_000}
      />
    );

    const expandedBody = screen.getByTestId('counter-card-expanded-body');
    expect(expandedBody).toBeInTheDocument();

    const itemsContainer = screen.getByTestId('counter-card-items');
    await user.click(itemsContainer);
    expect(onToggle).not.toHaveBeenCalled();

    const deliveryContainer = screen.getByTestId(
      'counter-card-delivery-container'
    );
    await user.click(deliveryContainer);
    expect(onToggle).not.toHaveBeenCalled();

    // Clicking the header collapses the card
    const header = screen.getByTestId('counter-card-header');
    await user.click(header);
    expect(onToggle).toHaveBeenCalledTimes(1);
    expect(onToggle).toHaveBeenCalledWith(sampleOrder.ticketId);
  });

  it('T027: renders expanded verification block with 4-digit monospace PIN, diner fullName, avatar image, "[Cantidad]x [Plato]" items, delivery button container and zero monetary symbols', () => {
    const onToggle = vi.fn();
    const { container } = render(
      <CounterTicketCard
        order={sampleOrder}
        isExpanded={true}
        onToggle={onToggle}
        nowMs={readyAtMs + 2 * 60_000}
      />
    );

    // Spec 022 T016 (RF-004): the PIN is rendered by the shared SecurityPinDisplay as "d d d d".
    const securityCodeEl = within(screen.getByTestId('counter-card-security-code')).getByTestId('security-pin-display');
    expect(securityCodeEl.textContent).toBe('4 8 2 1');
    expect(securityCodeEl).toHaveClass('font-mono', 'tracking-[0.25em]');

    expect(screen.getByText('Valentino Carmona')).toBeInTheDocument();
    const avatarImg = screen.getByTestId(
      'counter-card-customer-avatar'
    ) as HTMLImageElement;
    expect(avatarImg).toBeInTheDocument();
    expect(avatarImg.getAttribute('src')).toBe(
      '/api/v1/profile/users/c71e2289-4b68-45e0-a7d1-e6e7d6928e01/avatar'
    );

    expect(
      screen.getByText('2x Hamburguesa Completa con Papas')
    ).toBeInTheDocument();
    expect(
      screen.getByText('1x Limonada con Menta y Jengibre')
    ).toBeInTheDocument();

    expect(
      screen.getByTestId('counter-card-delivery-container')
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('counter-card-delivery-button')
    ).toHaveTextContent('ENTREGAR PEDIDO');

    expect(container.textContent).not.toContain('$');
  });

  it('T027: cleanly omits avatar image container when photoUrl is null or when avatar <img> emits onError (404)', () => {
    const onToggle = vi.fn();
    const orderWithoutPhoto: CounterReadyOrder = {
      ...sampleOrder,
      customer: {
        ...sampleOrder.customer,
        photoUrl: null,
      },
    };

    const { rerender } = render(
      <CounterTicketCard
        order={orderWithoutPhoto}
        isExpanded={true}
        onToggle={onToggle}
        nowMs={readyAtMs + 60_000}
      />
    );

    expect(
      screen.queryByTestId('counter-card-customer-avatar')
    ).not.toBeInTheDocument();
    expect(screen.getByText('Valentino Carmona')).toBeInTheDocument();

    // Rerender with photoUrl present, then simulate image 404/error
    rerender(
      <CounterTicketCard
        order={sampleOrder}
        isExpanded={true}
        onToggle={onToggle}
        nowMs={readyAtMs + 60_000}
      />
    );

    const avatarImg = screen.getByTestId('counter-card-customer-avatar');
    expect(avatarImg).toBeInTheDocument();

    fireEvent.error(avatarImg);

    expect(
      screen.queryByTestId('counter-card-customer-avatar')
    ).not.toBeInTheDocument();
    expect(screen.getByText('Valentino Carmona')).toBeInTheDocument();
  });
});

describe('CounterTicketCard PIN (Spec 022 T016, US1, RF-004, edge case 10)', () => {
  const nowMs = Date.parse(sampleOrder.readyAt) + 60_000;

  it('shows the stored PIN through SecurityPinDisplay, never the bare unspaced string', () => {
    render(<CounterTicketCard order={sampleOrder} isExpanded={true} onToggle={vi.fn()} nowMs={nowMs} />);

    const box = screen.getByTestId('counter-card-security-code');
    expect(within(box).getByTestId('security-pin-display').textContent).toBe('4 8 2 1');
    expect(screen.queryByText('4821')).not.toBeInTheDocument();
  });

  it('shows the neutral "- - - -" without blocking the card or the delivery control when the PIN is null', () => {
    const orderWithoutPin: CounterReadyOrder = { ...sampleOrder, securityCode: null };
    render(<CounterTicketCard order={orderWithoutPin} isExpanded={true} onToggle={vi.fn()} nowMs={nowMs} />);

    const box = screen.getByTestId('counter-card-security-code');
    expect(within(box).getByTestId('security-pin-display').textContent).toBe('- - - -');
    expect(screen.getByText('Valentino Carmona')).toBeInTheDocument();
    expect(screen.getByTestId('counter-card-delivery-container')).toBeInTheDocument();
  });
});
