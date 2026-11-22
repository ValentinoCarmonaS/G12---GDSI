import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { dishReviewApi } from '@/api/dishReviewApi';

export interface AdminPendingDishesContextValue {
  /** null while loading or on fetch error (never renders a fake 0 badge) */
  readonly pendingCount: number | null;
  readonly setPendingCount: (count: number) => void;
  readonly decrementPendingCount: () => void;
  readonly refreshPendingCount: () => Promise<void>;
}

const AdminPendingDishesContext = createContext<AdminPendingDishesContextValue | undefined>(
  undefined
);

export const AdminPendingDishesProvider: React.FC<{ readonly children: React.ReactNode }> = ({
  children,
}) => {
  const [pendingCount, setPendingCountState] = useState<number | null>(null);

  const refreshPendingCount = useCallback(async () => {
    try {
      const page = await dishReviewApi.getInbox({ tab: 'PENDING', page: 0, size: 1 });
      setPendingCountState(page.totalElements);
    } catch {
      // Keep null on error so the UI never displays a false 0 badge.
    }
  }, []);

  useEffect(() => {
    void refreshPendingCount();
  }, [refreshPendingCount]);

  const setPendingCount = useCallback((count: number) => {
    setPendingCountState(Math.max(0, count));
  }, []);

  const decrementPendingCount = useCallback(() => {
    setPendingCountState((prev) => (prev === null ? null : Math.max(0, prev - 1)));
  }, []);

  const value = useMemo<AdminPendingDishesContextValue>(
    () => ({
      pendingCount,
      setPendingCount,
      decrementPendingCount,
      refreshPendingCount,
    }),
    [pendingCount, setPendingCount, decrementPendingCount, refreshPendingCount]
  );

  return (
    <AdminPendingDishesContext.Provider value={value}>
      {children}
    </AdminPendingDishesContext.Provider>
  );
};

export function useAdminPendingDishes(): AdminPendingDishesContextValue {
  const ctx = useContext(AdminPendingDishesContext);
  if (!ctx) {
    throw new Error('useAdminPendingDishes must be used within AdminPendingDishesProvider');
  }
  return ctx;
}
