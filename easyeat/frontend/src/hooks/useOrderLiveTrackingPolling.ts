import { useState, useEffect, useRef, useCallback } from 'react';
import { orderTrackingApi } from '../api/orderTrackingApi';
import type { OrderLiveTrackingResponse } from '../types/orderTracking';

const TERMINAL_TRACKING_STATUSES = new Set<string>([
  'READY_FOR_PICKUP',
  'COMPLETED',
  'CANCELLED',
  'EXPIRED',
]);

function isTerminalTracking(current: OrderLiveTrackingResponse | null): boolean {
  if (!current) return false;
  return (
    current.isReadyForPickup === true ||
    TERMINAL_TRACKING_STATUSES.has(current.status)
  );
}

export interface UseOrderLiveTrackingPollingResult {
  tracking: OrderLiveTrackingResponse | null;
  isLoading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
}

export function useOrderLiveTrackingPolling(
  orderId: string,
  initialTracking?: OrderLiveTrackingResponse | null
): UseOrderLiveTrackingPollingResult {
  const [tracking, setTracking] = useState<OrderLiveTrackingResponse | null>(initialTracking || null);
  const [isLoading, setIsLoading] = useState<boolean>(!initialTracking);
  const [error, setError] = useState<Error | null>(null);

  const trackingRef = useRef<OrderLiveTrackingResponse | null>(tracking);
  trackingRef.current = tracking;

  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

  const fetchTracking = useCallback(async () => {
    if (!orderId) return;

    // Halt permanently if already terminal
    if (isTerminalTracking(trackingRef.current)) {
      return;
    }

    try {
      abortControllerRef.current?.abort();
      abortControllerRef.current = new AbortController();

      const data = await orderTrackingApi.getLiveTracking(orderId);
      setTracking(data);
      trackingRef.current = data;
      setError(null);
    } catch (err: unknown) {
      if ((err as Error)?.name !== 'AbortError') {
        setError(err instanceof Error ? err : new Error('Error al consultar seguimiento en vivo'));
      }
    } finally {
      setIsLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    let isMounted = true;

    const getCadence = (current: OrderLiveTrackingResponse | null) => {
      // 3000ms if proximity countdown active, 5000ms normal cadence
      if (current?.isCountdownActive && (current?.estimatedMinutesRemaining != null && current.estimatedMinutesRemaining <= 15)) {
        return 3000;
      }
      return 5000;
    };

    const scheduleNext = () => {
      if (!isMounted) return;
      if (isTerminalTracking(trackingRef.current)) return;
      if (document.visibilityState === 'hidden') return;

      const intervalMs = getCadence(trackingRef.current);
      timerRef.current = setTimeout(async () => {
        if (!isMounted) return;
        if (isTerminalTracking(trackingRef.current)) return;
        if (document.visibilityState === 'hidden') return;

        await fetchTracking();
        scheduleNext();
      }, intervalMs);
    };

    // If initial tracking not provided, fetch immediately at t=0
    if (!initialTracking) {
      fetchTracking().then(() => {
        scheduleNext();
      });
    } else {
      scheduleNext();
    }

    // Handle Page Visibility API
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        if (!isTerminalTracking(trackingRef.current)) {
          if (timerRef.current) clearTimeout(timerRef.current);
          fetchTracking().then(() => {
            scheduleNext();
          });
        }
      } else {
        if (timerRef.current) {
          clearTimeout(timerRef.current);
          timerRef.current = null;
        }
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      isMounted = false;
      if (timerRef.current) clearTimeout(timerRef.current);
      abortControllerRef.current?.abort();
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [orderId, initialTracking, fetchTracking]);

  return {
    tracking,
    isLoading,
    error,
    refetch: fetchTracking,
  };
}
