import React from 'react';
import type { FulfillmentMode } from '../../types/orderTracking';

export interface LiveTrackingWidgetProps {
  orderId: string;
  ticketNumber: number;
  fulfillmentMode: FulfillmentMode;
  timeSlotStart?: string | null;
}

export const LiveTrackingWidget: React.FC<LiveTrackingWidgetProps> = () => {
  return <div data-testid="live-tracking-widget-stub">Stub</div>;
};
