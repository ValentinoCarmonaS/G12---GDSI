import React from 'react';

export interface ReadyForPickupCalloutProps {
  ticketNumber: number;
  message?: string;
}

export const ReadyForPickupCallout: React.FC<ReadyForPickupCalloutProps> = () => {
  return <div data-testid="ready-for-pickup-stub">Stub</div>;
};
