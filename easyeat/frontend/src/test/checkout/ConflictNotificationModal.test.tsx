import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ConflictNotificationModal } from '../../components/checkout/ConflictNotificationModal';

describe('ConflictNotificationModal Component (T039 / US4)', () => {
  it('does not render when isOpen is false', () => {
    render(
      <ConflictNotificationModal
        isOpen={false}
        errorCode="SLOT_SOLD_OUT"
        onClose={vi.fn()}
      />
    );

    expect(screen.queryByTestId('conflict-notification-modal')).not.toBeInTheDocument();
  });

  it('renders slot sold out conflict message and triggers onRefreshSlots', () => {
    const onRefreshMock = vi.fn();
    const onCloseMock = vi.fn();

    render(
      <ConflictNotificationModal
        isOpen={true}
        errorCode="SLOT_SOLD_OUT"
        onClose={onCloseMock}
        onRefreshSlots={onRefreshMock}
      />
    );

    expect(screen.getByTestId('conflict-notification-modal')).toBeInTheDocument();
    expect(screen.getByText(/franja horaria agotada/i)).toBeInTheDocument();
    expect(
      screen.getByText(/la franja horaria seleccionada acaba de agotarse/i)
    ).toBeInTheDocument();

    const actionButton = screen.getByRole('button', { name: /elegir otra franja/i });
    fireEvent.click(actionButton);

    expect(onRefreshMock).toHaveBeenCalledTimes(1);
  });

  it('renders active reservation exists conflict message and triggers onViewExistingOrder', () => {
    const onViewOrderMock = vi.fn();
    const onCloseMock = vi.fn();

    render(
      <ConflictNotificationModal
        isOpen={true}
        errorCode="ACTIVE_RESERVATION_EXISTS"
        onClose={onCloseMock}
        onViewExistingOrder={onViewOrderMock}
      />
    );

    expect(screen.getByText(/reserva activa en curso/i)).toBeInTheDocument();
    expect(
      screen.getByText(/ya poseés una orden pendiente de confirmación/i)
    ).toBeInTheDocument();

    const viewButton = screen.getByRole('button', { name: /ver mi reserva/i });
    fireEvent.click(viewButton);

    expect(onViewOrderMock).toHaveBeenCalledTimes(1);
  });
});
