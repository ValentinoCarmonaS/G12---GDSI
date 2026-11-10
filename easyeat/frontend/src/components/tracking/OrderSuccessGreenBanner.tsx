import React from 'react';

export interface OrderSuccessGreenBannerProps {
  ticketNumber: number;
  pickupDate: string;
  timeSlotStart: string;
  timeSlotEnd: string;
  establishmentName: string;
  fulfillmentMode: 'IMMEDIATE' | 'SCHEDULED';
}

export const OrderSuccessGreenBanner: React.FC<OrderSuccessGreenBannerProps> = () => {
  return <div data-testid="order-success-green-banner">Stub</div>;
};
