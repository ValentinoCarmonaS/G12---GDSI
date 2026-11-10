import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ScheduledSlotCountdown } from '../../components/tracking/ScheduledSlotCountdown';

describe('T029 [US2]: ScheduledSlotCountdown Component Tests', () => {
  it('renders static time slot card and suppresses active countdown when isCountdownActive is false (D-04, CB-005)', () => {
    render(
      <ScheduledSlotCountdown
        isCountdownActive={false}
        timeSlotStart="13:00:00"
        timeSlotEnd="13:30:00"
        estimatedMinutesRemaining={null}
      />
    );

    // Static card should show the scheduled time window
    expect(screen.getByText(/13:00/)).toBeInTheDocument();
    expect(screen.getByText(/13:30/)).toBeInTheDocument();
    expect(screen.getByText(/Horario de retiro programado/i)).toBeInTheDocument();

    // Active countdown should strictly NOT be rendered
    expect(screen.queryByTestId('active-proximity-countdown')).not.toBeInTheDocument();
  });

  it('activates countdown display when isCountdownActive is true in proximity phase <= 15m (FR-008, AS-6, CB-006)', () => {
    render(
      <ScheduledSlotCountdown
        isCountdownActive={true}
        timeSlotStart="13:00:00"
        timeSlotEnd="13:30:00"
        estimatedMinutesRemaining={12}
      />
    );

    // Should render active countdown container
    const countdown = screen.getByTestId('active-proximity-countdown');
    expect(countdown).toBeInTheDocument();
    expect(screen.getByText(/12\s*min/i)).toBeInTheDocument();
    expect(screen.getByText(/¡Tu turno se acerca!/i)).toBeInTheDocument();
  });
});
