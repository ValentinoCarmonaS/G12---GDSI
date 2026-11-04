import { render, screen, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { PendingOrderCountdown } from '../../components/checkout/PendingOrderCountdown';

describe('PendingOrderCountdown Component (T034 / US4)', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders initial 15:00 countdown timer correctly', () => {
    const now = new Date('2026-11-03T12:00:00Z');
    const expiresAt = new Date('2026-11-03T12:15:00Z').toISOString();
    vi.setSystemTime(now);

    render(
      <PendingOrderCountdown
        orderId="order-123"
        expiresAt={expiresAt}
        serverTime={now.toISOString()}
      />
    );

    expect(screen.getByText(/15:00/)).toBeInTheDocument();
    expect(screen.getByText(/Reserva en espera de pago/i)).toBeInTheDocument();
  });

  it('updates countdown timer every second', () => {
    const now = new Date('2026-11-03T12:00:00Z');
    const expiresAt = new Date('2026-11-03T12:15:00Z').toISOString();
    vi.setSystemTime(now);

    render(
      <PendingOrderCountdown
        orderId="order-123"
        expiresAt={expiresAt}
        serverTime={now.toISOString()}
      />
    );

    act(() => {
      vi.advanceTimersByTime(5000); // 5 seconds
    });

    expect(screen.getByText(/14:55/)).toBeInTheDocument();
  });

  it('displays warning banner when remaining time is less than 2 minutes (< 120s)', () => {
    const now = new Date('2026-11-03T12:00:00Z');
    // 1 minute and 50 seconds remaining (110s)
    const expiresAt = new Date('2026-11-03T12:01:50Z').toISOString();
    vi.setSystemTime(now);

    render(
      <PendingOrderCountdown
        orderId="order-123"
        expiresAt={expiresAt}
        serverTime={now.toISOString()}
      />
    );

    expect(screen.getByText(/01:50/)).toBeInTheDocument();
    expect(screen.getByTestId('countdown-warning-banner')).toBeInTheDocument();
    expect(
      screen.getByText(/tu reserva está a punto de expirar/i)
    ).toBeInTheDocument();
  });

  it('triggers voluntary cancel callback when clicking cancel button', () => {
    const onCancelMock = vi.fn();
    const now = new Date('2026-11-03T12:00:00Z');
    const expiresAt = new Date('2026-11-03T12:10:00Z').toISOString();
    vi.setSystemTime(now);

    render(
      <PendingOrderCountdown
        orderId="order-123"
        expiresAt={expiresAt}
        onCancel={onCancelMock}
      />
    );

    const cancelButton = screen.getByRole('button', { name: /cancelar reserva/i });
    fireEvent.click(cancelButton);

    expect(onCancelMock).toHaveBeenCalledTimes(1);
  });

  it('triggers onExpire callback and displays expiration state when timer reaches 00:00', () => {
    const onExpireMock = vi.fn();
    const now = new Date('2026-11-03T12:00:00Z');
    const expiresAt = new Date('2026-11-03T12:00:03Z').toISOString(); // 3 seconds
    vi.setSystemTime(now);

    render(
      <PendingOrderCountdown
        orderId="order-123"
        expiresAt={expiresAt}
        onExpire={onExpireMock}
      />
    );

    expect(screen.getByText(/00:03/)).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(4000);
    });

    expect(screen.getByText(/00:00/)).toBeInTheDocument();
    expect(onExpireMock).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/reserva expirada/i)).toBeInTheDocument();
  });
});
