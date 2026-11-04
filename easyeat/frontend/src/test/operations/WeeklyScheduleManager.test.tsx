import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { WeeklyScheduleManager } from '@/components/operations/WeeklyScheduleManager';
import type { OperatingSchedule } from '@/types/operations';

const monday: OperatingSchedule = {
  id: 'sch-1',
  dayOfWeek: 'MONDAY',
  openingTime: '11:00:00',
  closingTime: '16:00:00',
  isClosed: false,
  version: 3,
};

const DAYS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

describe('WeeklyScheduleManager (ADM-2.2.1, INV-7, EC-008, EC-012)', () => {
  it('renders the seven days from Monday to Sunday, a day without configuration is closed', () => {
    render(<WeeklyScheduleManager schedules={[monday]} onSave={vi.fn()} />);

    DAYS.forEach((day) => expect(screen.getByText(day)).toBeInTheDocument());
    expect(screen.getByLabelText('Apertura Lunes')).toHaveValue('11:00');
    expect(screen.getByLabelText('Cierre Lunes')).toHaveValue('16:00');
    expect(screen.getByLabelText('Cerrado Lunes')).not.toBeChecked();
    expect(screen.getByLabelText('Cerrado Martes')).toBeChecked();
    expect(screen.getByLabelText('Apertura Martes')).toBeDisabled();
  });

  it('closing a day disables its times and sends isClosed with the known version', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<WeeklyScheduleManager schedules={[monday]} onSave={onSave} />);

    await userEvent.click(screen.getByLabelText('Cerrado Lunes'));
    expect(screen.getByLabelText('Apertura Lunes')).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    expect(onSave).toHaveBeenCalledWith([
      { dayOfWeek: 'MONDAY', openingTime: '11:00:00', closingTime: '16:00:00', isClosed: true, expectedVersion: 3 },
    ]);
  });

  it('opens an unconfigured day with no expectedVersion and only sends the days that changed', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<WeeklyScheduleManager schedules={[monday]} onSave={onSave} />);

    await userEvent.click(screen.getByLabelText('Cerrado Martes'));
    fireEvent.change(screen.getByLabelText('Apertura Martes'), { target: { value: '12:00' } });
    fireEvent.change(screen.getByLabelText('Cierre Martes'), { target: { value: '14:30' } });
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    expect(onSave).toHaveBeenCalledWith([
      { dayOfWeek: 'TUESDAY', openingTime: '12:00:00', closingTime: '14:30:00', isClosed: false, expectedVersion: null },
    ]);
  });

  it('blocks the submit and explains it when the opening is not before the closing (INV-7)', async () => {
    const onSave = vi.fn();
    render(<WeeklyScheduleManager schedules={[monday]} onSave={onSave} />);

    fireEvent.change(screen.getByLabelText('Apertura Lunes'), { target: { value: '17:00' } });
    fireEvent.change(screen.getByLabelText('Cierre Lunes'), { target: { value: '15:00' } });
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    expect(screen.getByRole('alert')).toHaveTextContent('La hora de apertura de Lunes debe ser anterior a la de cierre.');
    expect(onSave).not.toHaveBeenCalled();
  });

  it('does not submit when nothing changed', async () => {
    const onSave = vi.fn();
    render(<WeeklyScheduleManager schedules={[monday]} onSave={onSave} />);

    expect(screen.getByRole('button', { name: /guardar/i })).toBeDisabled();
  });

  it('surfaces a 409 as a reload message and offers to reload (EC-012)', async () => {
    const onSave = vi.fn().mockRejectedValue({ response: { status: 409 } });
    const onReload = vi.fn();
    render(<WeeklyScheduleManager schedules={[monday]} onSave={onSave} onReload={onReload} />);

    await userEvent.click(screen.getByLabelText('Cerrado Lunes'));
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('La configuración fue modificada por otro administrador. Recargue la pantalla para ver los cambios.');
    await userEvent.click(screen.getByRole('button', { name: /recargar/i }));
    expect(onReload).toHaveBeenCalledTimes(1);
  });

  it('shows a generic error for any other failure', async () => {
    const onSave = vi.fn().mockRejectedValue(new Error('network'));
    render(<WeeklyScheduleManager schedules={[monday]} onSave={onSave} />);

    await userEvent.click(screen.getByLabelText('Cerrado Lunes'));
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo guardar el horario. Intente nuevamente.');
  });
});
