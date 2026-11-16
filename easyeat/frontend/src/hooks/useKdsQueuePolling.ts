import { useState, useEffect, useRef, useCallback } from 'react';
import { fetchKitchenOrders } from '../api/kitchenKdsApi';
import { KdsTicket } from '../types/kds';

const POLLING_INTERVAL_MS = 4000;
const MAX_BACKOFF_MS = 16000;

export interface UseKdsQueuePollingResult {
  tickets: KdsTicket[];
  isLoading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
}

export const useKdsQueuePolling = (): UseKdsQueuePollingResult => {
  const [tickets, setTickets] = useState<KdsTicket[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<Error | null>(null);

  const backoffRef = useRef<number>(POLLING_INTERVAL_MS);
  const timeoutIdRef = useRef<NodeJS.Timeout | null>(null);
  const isMountedRef = useRef<boolean>(true);

  const fetchQueue = useCallback(async () => {
    try {
      const response = await fetchKitchenOrders(undefined, 0, 100, true);
      if (isMountedRef.current) {
        setTickets(response.content as KdsTicket[]);
        setError(null);
        backoffRef.current = POLLING_INTERVAL_MS;
      }
    } catch (err: any) {
      if (isMountedRef.current) {
        setError(err instanceof Error ? err : new Error(String(err)));
        backoffRef.current = Math.min(backoffRef.current * 2, MAX_BACKOFF_MS);
      }
    } finally {
      if (isMountedRef.current) {
        setIsLoading(false);
      }
    }
  }, []);

  const scheduleNextPoll = useCallback(() => {
    if (timeoutIdRef.current) {
      clearTimeout(timeoutIdRef.current);
    }
    if (document.visibilityState === 'hidden') {
      return;
    }
    timeoutIdRef.current = setTimeout(async () => {
      await fetchQueue();
      if (isMountedRef.current) {
        scheduleNextPoll();
      }
    }, backoffRef.current);
  }, [fetchQueue]);

  useEffect(() => {
    isMountedRef.current = true;

    // Initial fetch
    fetchQueue().then(() => {
      if (isMountedRef.current) {
        scheduleNextPoll();
      }
    });

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        fetchQueue().then(() => {
          if (isMountedRef.current) {
            scheduleNextPoll();
          }
        });
      } else {
        if (timeoutIdRef.current) {
          clearTimeout(timeoutIdRef.current);
        }
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      isMountedRef.current = false;
      if (timeoutIdRef.current) {
        clearTimeout(timeoutIdRef.current);
      }
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [fetchQueue, scheduleNextPoll]);

  return {
    tickets,
    isLoading,
    error,
    refetch: fetchQueue,
  };
};
