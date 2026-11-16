import { useState, useEffect, useRef, useCallback } from 'react';
import { counterApi } from '../api/counterApi';
import type {
  CounterReadyOrder,
  CounterOrdersPollingState,
} from '../types/counter';

export const DEFAULT_COUNTER_POLLING_INTERVAL_MS = 4_000;
export const DEFAULT_DISCONNECTION_THRESHOLD_MS = 15_000;

export interface UseCounterOrdersPollingOptions {
  establishmentId?: string;
  intervalMs?: number;
  disconnectionThresholdMs?: number;
  enabled?: boolean;
}

export function useCounterOrdersPolling(
  options: UseCounterOrdersPollingOptions = {}
): CounterOrdersPollingState {
  const {
    establishmentId,
    intervalMs = DEFAULT_COUNTER_POLLING_INTERVAL_MS,
    disconnectionThresholdMs = DEFAULT_DISCONNECTION_THRESHOLD_MS,
    enabled = true,
  } = options;

  const [orders, setOrders] = useState<CounterReadyOrder[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isDisconnected, setIsDisconnected] = useState<boolean>(false);
  const [serverOffsetMs, setServerOffsetMs] = useState<number>(0);

  const isMountedRef = useRef<boolean>(true);
  const abortControllerRef = useRef<AbortController | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const lastSuccessfulSyncRef = useRef<number>(Date.now());
  const hasCalculatedSkewRef = useRef<boolean>(false);

  const executePoll = useCallback(async () => {
    if (!enabled) return;
    if (
      typeof document !== 'undefined' &&
      document.visibilityState === 'hidden'
    ) {
      return;
    }

    abortControllerRef.current?.abort();
    const controller = new AbortController();
    abortControllerRef.current = controller;

    try {
      const data = await counterApi.fetchCounterOrders({
        establishmentId,
        signal: controller.signal,
        onServerDate: (serverTimeMs: number) => {
          if (!hasCalculatedSkewRef.current && isMountedRef.current) {
            hasCalculatedSkewRef.current = true;
            setServerOffsetMs(serverTimeMs - Date.now());
          }
        },
      });

      if (!isMountedRef.current || controller.signal.aborted) {
        return;
      }

      lastSuccessfulSyncRef.current = Date.now();
      setOrders(Array.isArray(data) ? data : []);
      setIsDisconnected(false);
    } catch (err: unknown) {
      if (!isMountedRef.current || controller.signal.aborted) {
        return;
      }
      const errorName = (err as { name?: string })?.name;
      if (errorName === 'AbortError' || errorName === 'CanceledError') {
        return;
      }

      const elapsedSinceLastSuccessMs =
        Date.now() - lastSuccessfulSyncRef.current;
      if (elapsedSinceLastSuccessMs > disconnectionThresholdMs) {
        setIsDisconnected(true);
      }
    } finally {
      if (isMountedRef.current && !controller.signal.aborted) {
        setIsLoading(false);
      }
    }
  }, [enabled, establishmentId, disconnectionThresholdMs]);

  useEffect(() => {
    isMountedRef.current = true;
    lastSuccessfulSyncRef.current = Date.now();

    if (!enabled) {
      setIsLoading(false);
      return;
    }

    const startInterval = () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
      timerRef.current = setInterval(() => {
        void executePoll();
      }, intervalMs);
    };

    const stopInterval = () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };

    if (
      typeof document === 'undefined' ||
      document.visibilityState !== 'hidden'
    ) {
      void executePoll();
      startInterval();
    }

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        stopInterval();
      } else if (document.visibilityState === 'visible') {
        void executePoll();
        startInterval();
      }
    };

    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', handleVisibilityChange);
    }

    return () => {
      isMountedRef.current = false;
      stopInterval();
      abortControllerRef.current?.abort();
      if (typeof document !== 'undefined') {
        document.removeEventListener(
          'visibilitychange',
          handleVisibilityChange
        );
      }
    };
  }, [enabled, intervalMs, executePoll]);

  const refresh = useCallback(async () => {
    await executePoll();
  }, [executePoll]);

  return {
    orders,
    isLoading,
    isDisconnected,
    serverOffsetMs,
    refresh,
  };
}

export default useCounterOrdersPolling;
