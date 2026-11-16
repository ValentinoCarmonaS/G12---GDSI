import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { KitchenTicketDelayControl } from '../../components/kitchen/KitchenTicketDelayControl';
import * as kitchenKdsApi from '../../api/kitchenKdsApi';

vi.mock('../../api/kitchenKdsApi', () => ({
  addTicketDelay: vi.fn(),
}));

describe('T020 [US2]: KitchenTicketDelayControl Component Tests (Spec 021 - COM-7.3.2 BIS)', () => {
  const mockTicketId = '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d';

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders "+5" button and does not render delay badge when initialDelayMinutes is 0', () => {
    render(
      <KitchenTicketDelayControl
        ticketId={mockTicketId}
        initialDelayMinutes={0}
      />
    );

    const button = screen.getByRole('button', { name: /\+5/i });
    expect(button).toBeInTheDocument();
    expect(screen.queryByTestId('delay-badge')).not.toBeInTheDocument();
  });

  it('renders accumulated delay badge when initialDelayMinutes > 0', () => {
    render(
      <KitchenTicketDelayControl
        ticketId={mockTicketId}
        initialDelayMinutes={10}
      />
    );

    const badge = screen.getByTestId('delay-badge');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent('+10 min');
  });

  it('calls addTicketDelay API, disables button during flight, and updates badge on success (RF-012, RF-013)', async () => {
    vi.mocked(kitchenKdsApi.addTicketDelay).mockResolvedValueOnce({
      ticketId: mockTicketId,
      orderId: 'order-123',
      ticketNumber: 104,
      specificDelayMinutes: 5,
      estimatedTotalMinutes: 25,
      updatedAt: new Date().toISOString(),
    });

    const onDelayUpdated = vi.fn();

    render(
      <KitchenTicketDelayControl
        ticketId={mockTicketId}
        initialDelayMinutes={0}
        onDelayUpdated={onDelayUpdated}
      />
    );

    const button = screen.getByRole('button', { name: /\+5/i });
    fireEvent.click(button);

    // Button should be disabled during flight
    expect(button).toBeDisabled();

    await waitFor(() => {
      expect(kitchenKdsApi.addTicketDelay).toHaveBeenCalledWith(mockTicketId);
    });

    await waitFor(() => {
      expect(screen.getByTestId('delay-badge')).toHaveTextContent('+5 min');
    });

    expect(button).not.toBeDisabled();
    expect(onDelayUpdated).toHaveBeenCalledWith(5);
  });

  it('consolidates accumulated delay to +15 min on successive presses (RF-014, SC-004)', async () => {
    vi.mocked(kitchenKdsApi.addTicketDelay)
      .mockResolvedValueOnce({
        ticketId: mockTicketId,
        orderId: 'order-123',
        ticketNumber: 104,
        specificDelayMinutes: 5,
        estimatedTotalMinutes: 25,
        updatedAt: new Date().toISOString(),
      })
      .mockResolvedValueOnce({
        ticketId: mockTicketId,
        orderId: 'order-123',
        ticketNumber: 104,
        specificDelayMinutes: 10,
        estimatedTotalMinutes: 30,
        updatedAt: new Date().toISOString(),
      })
      .mockResolvedValueOnce({
        ticketId: mockTicketId,
        orderId: 'order-123',
        ticketNumber: 104,
        specificDelayMinutes: 15,
        estimatedTotalMinutes: 35,
        updatedAt: new Date().toISOString(),
      });

    render(
      <KitchenTicketDelayControl
        ticketId={mockTicketId}
        initialDelayMinutes={0}
      />
    );

    const button = screen.getByRole('button', { name: /\+5/i });

    // Click 1 -> +5 min
    fireEvent.click(button);
    await waitFor(() => {
      expect(screen.getByTestId('delay-badge')).toHaveTextContent('+5 min');
    });

    // Click 2 -> +10 min
    fireEvent.click(button);
    await waitFor(() => {
      expect(screen.getByTestId('delay-badge')).toHaveTextContent('+10 min');
    });

    // Click 3 -> +15 min
    fireEvent.click(button);
    await waitFor(() => {
      expect(screen.getByTestId('delay-badge')).toHaveTextContent('+15 min');
    });
  });
});
