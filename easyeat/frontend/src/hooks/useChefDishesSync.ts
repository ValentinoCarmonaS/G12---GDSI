import { useState, useEffect, useRef, useCallback } from 'react';
import { dishApi } from '@/api/dishApi';
import type { ChefDishOperationalStatusItem } from '@/types/dish';

export interface UseChefDishesSyncResult {
  items: ChefDishOperationalStatusItem[];
  isLoading: boolean;
  refresh: () => Promise<void>;
}

export function useChefDishesSync(enabled = true): UseChefDishesSyncResult {
  const [items, setItems] = useState<ChefDishOperationalStatusItem[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const etagRef = useRef<string | null>(null);
  const isPollingRef = useRef<boolean>(false);

  const fetchStatus = useCallback(async () => {
    if (!enabled || isPollingRef.current) return;
    isPollingRef.current = true;

    try {
      const response = await dishApi.getOperationalStatus(etagRef.current || undefined);
      if (response && response.etag && response.etag !== etagRef.current) {
        etagRef.current = response.etag;
        setItems(response.items || []);
      }
    } catch {
      // 304 Not Modified or network error ignored in polling
    } finally {
      isPollingRef.current = false;
    }
  }, [enabled]);

  useEffect(() => {
    if (!enabled) return;

    // Initial fetch
    fetchStatus();

    // 2500ms polling interval (D-006, INV-18)
    const interval = setInterval(() => {
      fetchStatus();
    }, 2500);

    return () => clearInterval(interval);
  }, [enabled, fetchStatus]);

  return {
    items,
    isLoading,
    refresh: fetchStatus,
  };
}
