import { useState, useCallback } from 'react';
import { FulfillmentMode, TimeSlotAvailability } from '../types/order';

export interface UseCheckoutFulfillmentReturn {
  fulfillmentMode: FulfillmentMode | null;
  selectedDate: string | null;
  selectedSlot: TimeSlotAvailability | null;
  setFulfillmentMode: (mode: FulfillmentMode | null) => void;
  setSelectedDate: (date: string | null) => void;
  setSelectedSlot: (slot: TimeSlotAvailability | null) => void;
  resetFulfillment: () => void;
}

/**
 * Hook to manage diner fulfillment mode and time-slot scheduling state in checkout (US1, US2).
 * Ensures that choosing IMMEDIATE clears any selected date and time-slot immediately (FR-003, AC-3).
 */
export function useCheckoutFulfillment(initialMode: FulfillmentMode | null = null): UseCheckoutFulfillmentReturn {
  const [fulfillmentMode, setModeState] = useState<FulfillmentMode | null>(initialMode);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [selectedSlot, setSelectedSlot] = useState<TimeSlotAvailability | null>(null);

  const setFulfillmentMode = useCallback((mode: FulfillmentMode | null) => {
    setModeState(mode);
    if (mode === 'IMMEDIATE') {
      // Clear selected date and slot when transitioning to immediate mode (FR-003)
      setSelectedDate(null);
      setSelectedSlot(null);
    }
  }, []);

  const resetFulfillment = useCallback(() => {
    setModeState(null);
    setSelectedDate(null);
    setSelectedSlot(null);
  }, []);

  return {
    fulfillmentMode,
    selectedDate,
    selectedSlot,
    setFulfillmentMode,
    setSelectedDate,
    setSelectedSlot,
    resetFulfillment,
  };
}
