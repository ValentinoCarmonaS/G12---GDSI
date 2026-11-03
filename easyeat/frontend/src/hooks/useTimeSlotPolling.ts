import { useEffect, useRef, useState, useCallback } from 'react';
import { TimeSlotAvailability } from '../types/order';
import { fetchPickupSlots } from '../api/orderApi';

export interface UseTimeSlotPollingOptions {
  establishmentId: string;
  date?: string;
  intervalMs?: number; // default 8000ms (5-10s range)
  enabled?: boolean;
}

export interface UseTimeSlotPollingReturn {
  slots: TimeSlotAvailability[];
  isLoading: boolean;
  isRefreshing: boolean;
  error: Error | null;
  refresh: () => Promise<void>;
}

export function useTimeSlotPolling({
  establishmentId,
  date,
  intervalMs = 8000,
  enabled = true,
}: UseTimeSlotPollingOptions): UseTimeSlotPollingReturn {
  const [slots, setSlots] = useState<TimeSlotAvailability[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<Error | null>(null);

  const abortControllerRef = useRef<AbortController | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const loadSlots = useCallback(
    async (isBackground = false) => {
      if (!establishmentId) {
        setSlots([]);
        setIsLoading(false);
        setIsRefreshing(false);
        return;
      }

      if (isBackground) {
        setIsRefreshing(true);
      } else {
        setIsLoading(true);
      }
      setError(null);

      // Abort preceding pending request if any
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
      abortControllerRef.current = new AbortController();

      try {
        const catalog = await fetchPickupSlots(establishmentId, date);
        setSlots(catalog.slots || []);
      } catch (err: any) {
        if (err?.name !== 'CanceledError' && err?.name !== 'AbortError') {
          setError(err instanceof Error ? err : new Error(String(err)));
        }
      } finally {
        if (isBackground) {
          setIsRefreshing(false);
        } else {
          setIsLoading(false);
        }
      }
    },
    [establishmentId, date]
  );

  const refresh = useCallback(async () => {
    await loadSlots(false);
  }, [loadSlots]);

  useEffect(() => {
    if (!enabled || !establishmentId) {
      setIsLoading(false);
      return;
    }

    let isMounted = true;

    // Initial fetch
    loadSlots(false);

    const setupTimer = () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      timerRef.current = setTimeout(async () => {
        if (!isMounted) return;
        if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
          return;
        }
        await loadSlots(true);
        if (isMounted) {
          setupTimer();
        }
      }, intervalMs);
    };

    setupTimer();

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        loadSlots(true);
        setupTimer();
      } else if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };

    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', handleVisibilityChange);
    }

    return () => {
      isMounted = false;
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
      if (typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', handleVisibilityChange);
      }
    };
  }, [establishmentId, date, intervalMs, enabled, loadSlots]);

  return {
    slots,
    isLoading,
    isRefreshing,
    error,
    refresh,
  };
}
