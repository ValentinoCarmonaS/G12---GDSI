import React from 'react';

export interface ScheduledSlotCountdownProps {
  isCountdownActive: boolean;
  timeSlotStart?: string | null;
  timeSlotEnd?: string | null;
  estimatedMinutesRemaining?: number | null;
}

export const ScheduledSlotCountdown: React.FC<ScheduledSlotCountdownProps> = () => {
  return <div data-testid="scheduled-slot-countdown-stub">Stub</div>;
};
