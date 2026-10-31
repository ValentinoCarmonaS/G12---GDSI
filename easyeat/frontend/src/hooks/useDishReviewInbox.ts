import { useCallback, useEffect, useRef, useState } from 'react';
import { dishReviewApi } from '../api/dishReviewApi';
import type { DishReviewInboxItem, InboxTab } from '../types/dishReview';

/** FR-003: the page size the contract publishes as the default. */
const PAGE_SIZE = 20;

/**
 * A failure described by what the Administrator can do about it, which is what FR-032 asks for.
 * A message alone leaves the interface guessing whether to offer a retry.
 */
export interface InboxError {
  kind: 'FORBIDDEN' | 'BAD_REQUEST' | 'SERVER' | 'TRANSPORT';
  message: string;
  /** False for a permission failure: offering "retry" there would be a lie. */
  retryable: boolean;
}

function describe(err: unknown): InboxError {
  const status = (err as { response?: { status?: number } })?.response?.status;
  const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;

  if (status === 403 || status === 401) {
    return {
      kind: 'FORBIDDEN',
      message: detail ?? 'No tenés permiso para ver la bandeja de revisión.',
      retryable: false,
    };
  }
  if (status === 400) {
    return {
      kind: 'BAD_REQUEST',
      message: detail ?? 'La vista solicitada no es válida.',
      retryable: false,
    };
  }
  if (typeof status === 'number' && status >= 500) {
    return {
      kind: 'SERVER',
      message: detail ?? 'El servidor no pudo responder. Probá de nuevo.',
      retryable: true,
    };
  }
  return {
    kind: 'TRANSPORT',
    message: 'No se pudo conectar con el servidor. Probá de nuevo.',
    retryable: true,
  };
}

/**
 * Tab and page state for the segmented review inbox (FR-003), with the reconciliation FR-032
 * requires after a decision the Administrator lost to someone else.
 */
export function useDishReviewInbox() {
  const [tab, setTabState] = useState<InboxTab>('PENDING');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<DishReviewInboxItem[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [pendingCount, setPendingCount] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<InboxError | null>(null);

  /** Guards against a slow response from an abandoned tab overwriting the current one. */
  const requestSeq = useRef(0);

  const load = useCallback(async (targetTab: InboxTab, targetPage: number) => {
    const seq = ++requestSeq.current;
    setIsLoading(true);
    try {
      const data = await dishReviewApi.getInbox({
        tab: targetTab,
        page: targetPage,
        size: PAGE_SIZE,
      });
      if (seq !== requestSeq.current) {
        return;
      }
      // Items are replaced only on success. A list that blanks while a refetch is in flight
      // makes the Administrator lose their place for no benefit.
      setItems(data.items);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
      setHasNext(data.hasNext);
      setPendingCount(data.pendingCount);
      setError(null);
    } catch (err) {
      if (seq !== requestSeq.current) {
        return;
      }
      setError(describe(err));
    } finally {
      if (seq === requestSeq.current) {
        setIsLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    load(tab, page);
  }, [load, tab, page]);

  /**
   * Switching views resets to the first page, because page 3 of one tab means nothing in
   * another. Selecting the tab already active does not, since clicking where you already are
   * must not cost you your place.
   */
  const setTab = useCallback(
    (next: InboxTab) => {
      setTabState((current) => {
        if (current !== next) {
          setPage(0);
        }
        return next;
      });
    },
    []
  );

  const refresh = useCallback(() => {
    load(tab, page);
  }, [load, tab, page]);

  /**
   * FR-032: a dish that has already received a terminal decision is removed from the view, and
   * the view is reconciled so nothing left behind still describes it.
   *
   * Three steps, and the third is the one that is easy to miss. The row goes immediately, so the
   * Administrator sees the consequence of the conflict they were just told about. The data is
   * refetched, so the badge and the totals stop counting it. And if that row was the last one on
   * the page, the view steps back — otherwise the Administrator is stranded on an empty page
   * with no way forward.
   */
  const reconcileAfterStaleDecision = useCallback(
    (dishId: string) => {
      // Computed from the rendered state rather than inside a setItems updater: React does not
      // run an updater synchronously, so a flag set in there is still false on the next line.
      // That mistake made this function silently stop stepping back, which is why T047 covers it.
      const remaining = items.filter((candidate) => candidate.dishId !== dishId);
      const wasLastOnPage = remaining.length === 0 && items.length > 0;

      setItems(remaining);

      if (wasLastOnPage && page > 0) {
        // Stepping back refetches through the page effect, so no explicit load is needed here.
        setPage((current) => Math.max(0, current - 1));
        return;
      }
      load(tab, page);
    },
    [items, load, tab, page]
  );

  return {
    tab,
    page,
    items,
    totalElements,
    totalPages,
    hasNext,
    pendingCount,
    isLoading,
    error,
    pageSize: PAGE_SIZE,
    setTab,
    setPage,
    refresh,
    reconcileAfterStaleDecision,
  };
}
