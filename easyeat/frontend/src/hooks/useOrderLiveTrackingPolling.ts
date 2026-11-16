import { useState, useEffect, useRef, useCallback } from 'react';
import { orderTrackingApi } from '../api/orderTrackingApi';
import type { OrderLiveTrackingResponse } from '../types/orderTracking';

const TERMINAL_TRACKING_STATUSES = new Set<string>([
  'READY_FOR_PICKUP',
  'COMPLETED',
  'CANCELLED',
  'EXPIRED',
]);

const POLLING_CADENCE_MS = 6000;
const DISCONNECTION_THRESHOLD_MS = 15000;

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
  isDisconnected: boolean;
}

export function useOrderLiveTrackingPolling(
  orderId: string,
  initialTracking?: OrderLiveTrackingResponse | null
): UseOrderLiveTrackingPollingResult {
  const [tracking, setTracking] = useState<OrderLiveTrackingResponse | null>(initialTracking || null);
  const [isLoading, setIsLoading] = useState<boolean>(!initialTracking);
  const [error, setError] = useState<Error | null>(null);
  const [isDisconnected, setIsDisconnected] = useState<boolean>(false);

  const trackingRef = useRef<OrderLiveTrackingResponse | null>(tracking);
  trackingRef.current = tracking;

  const lastSuccessTimeRef = useRef<number>(Date.now());
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
      
      // Visual idempotence: only update state if content actually changed
      const currentJson = JSON.stringify(trackingRef.current);
      const newJson = JSON.stringify(data);
      if (currentJson !== newJson) {
        setTracking(data);
      }
      trackingRef.current = data;

      lastSuccessTimeRef.current = Date.now();
      setIsDisconnected(false);
      setError(null);
    } catch (err: unknown) {
      if ((err as Error)?.name !== 'AbortError') {
        const errorObj = err instanceof Error ? err : new Error('Error al consultar seguimiento en vivo');
        setError(errorObj);

        const elapsed = Date.now() - lastSuccessTimeRef.current;
        if (elapsed > DISCONNECTION_THRESHOLD_MS) {
          setIsDisconnected(true);
        }
      }
    } finally {
      setIsLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    let isMounted = true;

    const scheduleNext = () => {
      if (!isMounted) return;
      if (isTerminalTracking(trackingRef.current)) return;
      if (document.visibilityState === 'hidden') return;

      timerRef.current = setTimeout(async () => {
        if (!isMounted) return;
        if (isTerminalTracking(trackingRef.current)) return;
        if (document.visibilityState === 'hidden') return;

        await fetchTracking();
        scheduleNext();
      }, POLLING_CADENCE_MS);
    };

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
    isDisconnected,
  };
}
