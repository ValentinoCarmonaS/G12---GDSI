import { useState, useEffect, useRef, useCallback } from 'react';
import { dinerCatalogApi } from '@/api/dinerCatalogApi';

export interface UseRealtimeAvailabilityOptions {
  tenantId?: string | null;
  enabled?: boolean;
  intervalMs?: number;
  initialAvailability?: Record<string, boolean>;
  onErrorThreshold?: () => void;
  onRecovery?: () => void;
}

export interface UseRealtimeAvailabilityResult {
  availability: Record<string, boolean>;
  isConnectionLost: boolean;
  isLoading: boolean;
  etag: string | null;
  refresh: () => Promise<void>;
}

export function useRealtimeAvailability({
  tenantId,
  enabled = true,
  intervalMs = 2500,
  initialAvailability = {},
  onErrorThreshold,
  onRecovery,
}: UseRealtimeAvailabilityOptions): UseRealtimeAvailabilityResult {
  const [availability, setAvailability] = useState<Record<string, boolean>>(initialAvailability);
  const [isConnectionLost, setIsConnectionLost] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(false);

  const etagRef = useRef<string | null>(null);
  const consecutiveErrorsRef = useRef<number>(0);
  const isPollingRef = useRef<boolean>(false);
  const isMountedRef = useRef<boolean>(true);

  // Sync initial availability if provided externally
  useEffect(() => {
    if (initialAvailability && Object.keys(initialAvailability).length > 0) {
      setAvailability((prev) => ({ ...prev, ...initialAvailability }));
    }
  }, [initialAvailability]);

  const poll = useCallback(async () => {
    if (!tenantId || !enabled || isPollingRef.current) {
      return;
    }

    if (typeof document !== 'undefined' && document.hidden) {
      return;
    }

    isPollingRef.current = true;
    try {
      const result = await dinerCatalogApi.getCatalogAvailability(tenantId, etagRef.current);

      if (!isMountedRef.current) return;

      if (result.status === 200 && result.data?.availability) {
        setAvailability(result.data.availability);
        if (result.etag) {
          etagRef.current = result.etag;
        }
      }

      if (consecutiveErrorsRef.current >= 3) {
        setIsConnectionLost(false);
        onRecovery?.();
      }
      consecutiveErrorsRef.current = 0;
    } catch (error) {
      if (!isMountedRef.current) return;

      consecutiveErrorsRef.current += 1;
      if (consecutiveErrorsRef.current >= 3) {
        setIsConnectionLost(true);
        if (consecutiveErrorsRef.current === 3) {
          onErrorThreshold?.();
        }
      }
    } finally {
      isPollingRef.current = false;
      if (isMountedRef.current) {
        setIsLoading(false);
      }
    }
  }, [tenantId, enabled, onErrorThreshold, onRecovery]);

  useEffect(() => {
    isMountedRef.current = true;
    consecutiveErrorsRef.current = 0;
    etagRef.current = null;

    if (!tenantId || !enabled) {
      return;
    }

    // Immediate initial poll
    poll();

    // Start polling timer
    let timerId: ReturnType<typeof setInterval> | null = setInterval(poll, intervalMs);

    // Page Visibility API handler
    const handleVisibilityChange = () => {
      if (document.hidden) {
        if (timerId) {
          clearInterval(timerId);
          timerId = null;
        }
      } else {
        if (!timerId) {
          poll();
          timerId = setInterval(poll, intervalMs);
        }
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      isMountedRef.current = false;
      if (timerId) {
        clearInterval(timerId);
      }
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [tenantId, enabled, intervalMs, poll]);

  const refresh = useCallback(async () => {
    etagRef.current = null;
    await poll();
  }, [poll]);

  return {
    availability,
    isConnectionLost,
    isLoading,
    etag: etagRef.current,
    refresh,
  };
}

export default useRealtimeAvailability;
