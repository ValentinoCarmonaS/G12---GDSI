import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SlotCapacityGrid } from '@/components/operations/SlotCapacityGrid';
import type { SlotCapacity } from '@/types/operations';

const DATE = '2026-11-11';

function slot(startTime: string, endTime: string, overrides: Partial<SlotCapacity> = {}): SlotCapacity {
  return {
    id: null,
    slotDate: DATE,
    startTime,
    endTime,
    maxCapacity: 0,
    approvedOrdersCount: 0,
    isSoldOut: true,
    version: null,
    ...overrides,
  };
}

const matrix: SlotCapacity[] = [
  slot('12:00:00', '12:15:00'),
  slot('12:15:00', '12:30:00', { id: 's-2', maxCapacity: 20, approvedOrdersCount: 5, isSoldOut: false, version: 3 }),
  slot('12:30:00', '12:45:00', { id: 's-3', maxCapacity: 10, approvedOrdersCount: 10, isSoldOut: true, version: 1 }),
  slot('12:45:00', '13:00:00'),
];

function setup(overrides: Partial<React.ComponentProps<typeof SlotCapacityGrid>> = {}) {
  const props = {
    date: DATE,
    slots: matrix,
    onDateChange: vi.fn(),
    onSave: vi.fn().mockResolvedValue(undefined),
    loadDay: vi.fn(),
    onReload: vi.fn(),
    ...overrides,
  };
  render(<SlotCapacityGrid {...props} />);
  return props;
}

describe('SlotCapacityGrid (ADM-2.2.2, INV-3, INV-6, EC-007, SC-005)', () => {
  it('renders the 15-minute matrix with approved of max and an Agotada badge on full slots', () => {
    setup();

    expect(screen.getAllByRole('row')).toHaveLength(1 + matrix.length);
    expect(screen.getByLabelText('Capacidad 12:15')).toHaveValue(20);
    expect(screen.getByText('5 / 20')).toBeInTheDocument();
    expect(screen.getByText('10 / 10')).toBeInTheDocument();
    // 12:00, 12:30 and 12:45 are sold out (no capacity yet, or full)
    expect(screen.getAllByText('Agotada')).toHaveLength(3);
  });

  it('asks for another date through onDateChange', () => {
    const props = setup();

    fireEvent.change(screen.getByLabelText('Fecha'), { target: { value: '2026-11-12' } });

    expect(props.onDateChange).toHaveBeenCalledWith('2026-11-12');
  });

  it('sends only the edited cell with its known version, null for an unconfigured slot', async () => {
    const props = setup();

    fireEvent.change(screen.getByLabelText('Capacidad 12:15'), { target: { value: '30' } });
    fireEvent.change(screen.getByLabelText('Capacidad 12:00'), { target: { value: '15' } });
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    await waitFor(() => expect(props.onSave).toHaveBeenCalledTimes(1));
    expect(props.onSave).toHaveBeenCalledWith([
      { slotDate: DATE, startTime: '12:00:00', maxCapacity: 15, expectedVersion: null },
      { slotDate: DATE, startTime: '12:15:00', maxCapacity: 30, expectedVersion: 3 },
    ]);
  });

  it('keeps the save button disabled while nothing changed', () => {
    setup();

    expect(screen.getByRole('button', { name: /guardar/i })).toBeDisabled();
  });

  it('rejects a negative or non-integer capacity and does not save (INV-6)', async () => {
    const props = setup();

    fireEvent.change(screen.getByLabelText('Capacidad 12:15'), { target: { value: '-4' } });
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));
    expect(screen.getByRole('alert')).toHaveTextContent('La capacidad debe ser un número entero mayor o igual a 0.');

    fireEvent.change(screen.getByLabelText('Capacidad 12:15'), { target: { value: '2.5' } });
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));
    expect(screen.getByRole('alert')).toHaveTextContent('La capacidad debe ser un número entero mayor o igual a 0.');
    expect(props.onSave).not.toHaveBeenCalled();
  });

  it('bulk apply: one value on every slot of the date is one onSave call with the full list', async () => {
    const props = setup();

    fireEvent.change(screen.getByLabelText('Capacidad para todas las franjas'), { target: { value: '25' } });
    await userEvent.click(screen.getByRole('button', { name: /aplicar a todas/i }));
    expect(screen.getByLabelText('Capacidad 12:00')).toHaveValue(25);
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    await waitFor(() => expect(props.onSave).toHaveBeenCalledTimes(1));
    expect(props.onSave).toHaveBeenCalledWith([
      { slotDate: DATE, startTime: '12:00:00', maxCapacity: 25, expectedVersion: null },
      { slotDate: DATE, startTime: '12:15:00', maxCapacity: 25, expectedVersion: 3 },
      { slotDate: DATE, startTime: '12:30:00', maxCapacity: 25, expectedVersion: 1 },
      { slotDate: DATE, startTime: '12:45:00', maxCapacity: 25, expectedVersion: null },
    ]);
  });

  it('bulk apply over a date range loads each day and still sends a single list', async () => {
    const nextDay = [
      slot('12:00:00', '12:15:00', { slotDate: '2026-11-12' }),
      slot('12:15:00', '12:30:00', { slotDate: '2026-11-12', id: 'n-2', maxCapacity: 8, isSoldOut: false, version: 6 }),
    ];
    const loadDay = vi.fn().mockResolvedValue(nextDay);
    const props = setup({ loadDay });

    fireEvent.change(screen.getByLabelText('Capacidad para todas las franjas'), { target: { value: '40' } });
    fireEvent.change(screen.getByLabelText('Hasta (opcional)'), { target: { value: '2026-11-12' } });
    await userEvent.click(screen.getByRole('button', { name: /aplicar a todas/i }));
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    await waitFor(() => expect(props.onSave).toHaveBeenCalledTimes(1));
    expect(loadDay).toHaveBeenCalledTimes(1);
    expect(loadDay).toHaveBeenCalledWith('2026-11-12');
    const sent = vi.mocked(props.onSave).mock.calls[0][0];
    expect(sent).toHaveLength(matrix.length + nextDay.length);
    expect(sent).toContainEqual({ slotDate: '2026-11-12', startTime: '12:15:00', maxCapacity: 40, expectedVersion: 6 });
    expect(sent).toContainEqual({ slotDate: '2026-11-12', startTime: '12:00:00', maxCapacity: 40, expectedVersion: null });
  });

  it('refuses a range that ends before the date and a bulk value that is not valid', async () => {
    const props = setup();

    fireEvent.change(screen.getByLabelText('Capacidad para todas las franjas'), { target: { value: '-1' } });
    await userEvent.click(screen.getByRole('button', { name: /aplicar a todas/i }));
    expect(screen.getByRole('alert')).toHaveTextContent('La capacidad debe ser un número entero mayor o igual a 0.');

    fireEvent.change(screen.getByLabelText('Capacidad para todas las franjas'), { target: { value: '5' } });
    fireEvent.change(screen.getByLabelText('Hasta (opcional)'), { target: { value: '2026-11-01' } });
    await userEvent.click(screen.getByRole('button', { name: /aplicar a todas/i }));
    expect(screen.getByRole('alert')).toHaveTextContent('La fecha final debe ser igual o posterior a la fecha inicial.');
    expect(props.onSave).not.toHaveBeenCalled();
  });

  it('surfaces a 409 as a reload message and offers to reload (EC-012)', async () => {
    const onSave = vi.fn().mockRejectedValue({ response: { status: 409 } });
    const props = setup({ onSave });

    fireEvent.change(screen.getByLabelText('Capacidad 12:15'), { target: { value: '30' } });
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'La configuración fue modificada por otro administrador. Recargue la pantalla para ver los cambios.'
    );
    await userEvent.click(screen.getByRole('button', { name: /recargar/i }));
    expect(props.onReload).toHaveBeenCalledTimes(1);
  });

  it('shows a generic error for any other failure', async () => {
    setup({ onSave: vi.fn().mockRejectedValue(new Error('network')) });

    fireEvent.change(screen.getByLabelText('Capacidad 12:15'), { target: { value: '30' } });
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo guardar la capacidad. Intente nuevamente.');
  });

  it('shows an empty state when the day has no slots', () => {
    setup({ slots: [] });

    expect(screen.getByText('Este día no tiene franjas para configurar.')).toBeInTheDocument();
  });
});
