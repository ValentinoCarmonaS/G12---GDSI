import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DinerSlotSelector } from '@/components/operations/DinerSlotSelector';
import type { DinerTimeSlot } from '@/types/operations';

const slots: DinerTimeSlot[] = [
  { startTime: '12:00:00', endTime: '12:15:00', status: 'AVAILABLE' },
  { startTime: '12:15:00', endTime: '12:30:00', status: 'SOLD_OUT' },
  { startTime: '12:30:00', endTime: '12:45:00', status: 'AVAILABLE' },
];

function setup(overrides: Partial<React.ComponentProps<typeof DinerSlotSelector>> = {}) {
  const props = {
    slots,
    isClosed: false,
    onConfirm: vi.fn(),
    revalidate: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
  render(<DinerSlotSelector {...props} />);
  return props;
}

describe('DinerSlotSelector (INV-3, EC-007, EC-013)', () => {
  it('shouldRenderSoldOutBadge: a sold-out slot shows "Agotada" and cannot be selected', async () => {
    setup();

    expect(screen.getByText('Agotada')).toBeInTheDocument();
    const soldOut = screen.getByRole('button', { name: /^12:15 - / });
    expect(soldOut).toBeDisabled();
    await userEvent.click(soldOut);
    expect(screen.getByRole('button', { name: /confirmar/i })).toBeDisabled();
  });

  it('an available slot can be selected and confirmed with its start time', async () => {
    const props = setup();

    await userEvent.click(screen.getByRole('button', { name: /^12:30 - / }));
    expect(screen.getByRole('button', { name: /^12:30 - / })).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(screen.getByRole('button', { name: /confirmar/i }));

    await waitFor(() => expect(props.onConfirm).toHaveBeenCalledWith('12:30:00'));
  });

  it('shouldRevalidateSlotBeforeOrderCreation: it calls the injected revalidate with the slot before confirming', async () => {
    const order: string[] = [];
    const revalidate = vi.fn().mockImplementation(async () => {
      order.push('revalidate');
    });
    const onConfirm = vi.fn().mockImplementation(() => {
      order.push('confirm');
    });
    setup({ revalidate, onConfirm });

    await userEvent.click(screen.getByRole('button', { name: /^12:00 - / }));
    await userEvent.click(screen.getByRole('button', { name: /confirmar/i }));

    await waitFor(() => expect(onConfirm).toHaveBeenCalledTimes(1));
    expect(revalidate).toHaveBeenCalledWith('12:00:00');
    expect(order).toEqual(['revalidate', 'confirm']);
  });

  it('blocks with a message and does not confirm when the slot sold out meanwhile (409, EC-013)', async () => {
    const revalidate = vi.fn().mockRejectedValue({ response: { status: 409 } });
    const props = setup({ revalidate });

    await userEvent.click(screen.getByRole('button', { name: /^12:00 - / }));
    await userEvent.click(screen.getByRole('button', { name: /confirmar/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'La franja seleccionada se agotó mientras elegías. Por favor elegí otra franja.'
    );
    expect(props.onConfirm).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /confirmar/i })).toBeDisabled();
  });

  it('shows a generic message for any other revalidation failure and does not confirm', async () => {
    const props = setup({ revalidate: vi.fn().mockRejectedValue(new Error('network')) });

    await userEvent.click(screen.getByRole('button', { name: /^12:00 - / }));
    await userEvent.click(screen.getByRole('button', { name: /confirmar/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo verificar la franja. Intente nuevamente.');
    expect(props.onConfirm).not.toHaveBeenCalled();
  });

  it('a closed day shows the reason and offers no slots', () => {
    setup({ slots: [], isClosed: true, closureReason: 'Cocina cerrada para el día de hoy' });

    expect(screen.getByText('Cocina cerrada para el día de hoy')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /confirmar/i })).not.toBeInTheDocument();
  });

  it('every slot sold out is explained instead of an empty list', () => {
    setup({ slots: [slots[1]] });

    expect(screen.getByText('No quedan franjas disponibles para este día.')).toBeInTheDocument();
  });
});
