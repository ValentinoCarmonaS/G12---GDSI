import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useDishReviewInbox } from '../../src/hooks/useDishReviewInbox';
import { dishReviewApi } from '../../src/api/dishReviewApi';
import type { DishReviewInboxItem, DishReviewInboxPage } from '../../src/types/dishReview';

vi.mock('../../src/api/dishReviewApi', () => ({
  dishReviewApi: {
    getInbox: vi.fn(),
    getDetail: vi.fn(),
  },
}));

function item(id: string, name = `Plato ${id}`): DishReviewInboxItem {
  return {
    dishId: id,
    name,
    category: 'Platos principales',
    status: 'PENDING_PRICE',
    authorName: 'Camila Duarte',
    authorDeactivated: false,
    submittedAt: '2026-10-01T10:00:00Z',
    publishedAt: null,
    retailPrice: null,
    isResubmission: false,
    reviewCycleCount: 0,
  };
}

function page(overrides: Partial<DishReviewInboxPage> = {}): DishReviewInboxPage {
  return {
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    pendingCount: 0,
    ...overrides,
  };
}

describe('useDishReviewInbox', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(dishReviewApi.getInbox).mockResolvedValue(page());
  });

  describe('initial load', () => {
    it('loads the PENDING tab on page 0 without being asked', async () => {
      renderHook(() => useDishReviewInbox());

      await waitFor(() => {
        expect(dishReviewApi.getInbox).toHaveBeenCalledWith({
          tab: 'PENDING',
          page: 0,
          size: 20,
        });
      });
    });

    it('reports a loading state before the first response and clears it after', async () => {
      const { result } = renderHook(() => useDishReviewInbox());

      expect(result.current.isLoading).toBe(true);
      await waitFor(() => expect(result.current.isLoading).toBe(false));
    });

    it('exposes the returned items and metadata', async () => {
      vi.mocked(dishReviewApi.getInbox).mockResolvedValue(
        page({ items: [item('a'), item('b')], totalElements: 2, totalPages: 1, pendingCount: 2 })
      );

      const { result } = renderHook(() => useDishReviewInbox());

      await waitFor(() => expect(result.current.items).toHaveLength(2));
      expect(result.current.totalElements).toBe(2);
      expect(result.current.pendingCount).toBe(2);
    });
  });

  describe('tab and page state (FR-003)', () => {
    it('refetches when the tab changes, and resets to page 0', async () => {
      const { result } = renderHook(() => useDishReviewInbox());
      await waitFor(() => expect(result.current.isLoading).toBe(false));

      act(() => result.current.setPage(3));
      await waitFor(() =>
        expect(dishReviewApi.getInbox).toHaveBeenLastCalledWith(
          expect.objectContaining({ page: 3 })
        )
      );

      act(() => result.current.setTab('FINALIZED'));

      await waitFor(() =>
        expect(dishReviewApi.getInbox).toHaveBeenLastCalledWith({
          tab: 'FINALIZED',
          page: 0,
          size: 20,
        })
      );
      expect(result.current.page).toBe(0);
    });

    it('does not reset the page when the tab is set to the one already active', async () => {
      const { result } = renderHook(() => useDishReviewInbox());
      await waitFor(() => expect(result.current.isLoading).toBe(false));

      act(() => result.current.setPage(2));
      await waitFor(() => expect(result.current.page).toBe(2));

      act(() => result.current.setTab('PENDING'));

      // Staying on the same tab must not throw the Administrator back to the first page.
      expect(result.current.page).toBe(2);
    });

    it('keeps the pending badge across tabs, since the server sends it on every one', async () => {
      vi.mocked(dishReviewApi.getInbox).mockResolvedValue(page({ pendingCount: 37 }));

      const { result } = renderHook(() => useDishReviewInbox());
      await waitFor(() => expect(result.current.pendingCount).toBe(37));

      act(() => result.current.setTab('FINALIZED'));
      await waitFor(() => expect(result.current.tab).toBe('FINALIZED'));

      expect(result.current.pendingCount).toBe(37);
    });
  });

  describe('error handling (FR-032)', () => {
    it('surfaces an authorization failure as a non-recoverable notice', async () => {
      vi.mocked(dishReviewApi.getInbox).mockRejectedValue({
        response: { status: 403, data: { detail: 'No autorizado.' } },
      });

      const { result } = renderHook(() => useDishReviewInbox());

      await waitFor(() => expect(result.current.error).not.toBeNull());
      expect(result.current.error?.kind).toBe('FORBIDDEN');
      expect(result.current.error?.retryable)
        .toBe(false);
    });

    it('surfaces a transport failure as retryable, which a permission failure is not', async () => {
      vi.mocked(dishReviewApi.getInbox).mockRejectedValue({ message: 'Network Error' });

      const { result } = renderHook(() => useDishReviewInbox());

      await waitFor(() => expect(result.current.error).not.toBeNull());
      expect(result.current.error?.retryable).toBe(true);
    });

    it('clears a previous error on a successful refetch', async () => {
      vi.mocked(dishReviewApi.getInbox).mockRejectedValueOnce({ message: 'Network Error' });

      const { result } = renderHook(() => useDishReviewInbox());
      await waitFor(() => expect(result.current.error).not.toBeNull());

      vi.mocked(dishReviewApi.getInbox).mockResolvedValue(page({ items: [item('a')] }));
      act(() => {
        result.current.refresh();
      });

      await waitFor(() => expect(result.current.error).toBeNull());
      expect(result.current.items).toHaveLength(1);
    });

    it('leaves the previous items in place while a refetch is in flight', async () => {
      vi.mocked(dishReviewApi.getInbox).mockResolvedValue(page({ items: [item('a')] }));
      const { result } = renderHook(() => useDishReviewInbox());
      await waitFor(() => expect(result.current.items).toHaveLength(1));

      let resolve: (value: DishReviewInboxPage) => void = () => {};
      vi.mocked(dishReviewApi.getInbox).mockReturnValue(
        new Promise((r) => {
          resolve = r;
        })
      );

      act(() => {
        result.current.refresh();
      });

      // A list that blanks on every refresh makes the Administrator lose their place.
      expect(result.current.items).toHaveLength(1);
      await act(async () => {
        resolve(page({ items: [item('a'), item('b')] }));
      });
      await waitFor(() => expect(result.current.items).toHaveLength(2));
    });
  });

  describe('reconciliation after a stale decision (FR-032)', () => {
    it('removes the decided dish from the view immediately', async () => {
      vi.mocked(dishReviewApi.getInbox).mockResolvedValue(
        page({ items: [item('a'), item('b')], totalElements: 2, pendingCount: 2 })
      );
      const { result } = renderHook(() => useDishReviewInbox());
      await waitFor(() => expect(result.current.items).toHaveLength(2));

      act(() => result.current.reconcileAfterStaleDecision('a'));

      expect(result.current.items.map((i) => i.dishId))
        .toEqual(['b']);
    });

    it('refetches so the counts and pagination stop describing the removed row', async () => {
      vi.mocked(dishReviewApi.getInbox).mockResolvedValue(
        page({ items: [item('a'), item('b')], totalElements: 2, pendingCount: 2 })
      );
      const { result } = renderHook(() => useDishReviewInbox());
      await waitFor(() => expect(result.current.items).toHaveLength(2));

      vi.mocked(dishReviewApi.getInbox).mockResolvedValue(
        page({ items: [item('b')], totalElements: 1, totalPages: 1, pendingCount: 1 })
      );

      act(() => result.current.reconcileAfterStaleDecision('a'));

      await waitFor(() => expect(result.current.pendingCount).toBe(1));
      expect(result.current.totalElements).toBe(1);
    });

    it('removing the last row of a page steps back rather than stranding the view', async () => {
      vi.mocked(dishReviewApi.getInbox).mockResolvedValue(
        page({ items: [item('z')], page: 1, totalElements: 21, totalPages: 2, pendingCount: 21 })
      );
      const { result } = renderHook(() => useDishReviewInbox());
      await waitFor(() => expect(result.current.items).toHaveLength(1));
      act(() => result.current.setPage(1));
      await waitFor(() => expect(result.current.page).toBe(1));

      act(() => result.current.reconcileAfterStaleDecision('z'));

      // Page 1 now holds nothing; leaving the Administrator on an empty page they cannot
      // leave by paging forward is the defect FR-032's reconciliation clause describes.
      await waitFor(() => expect(result.current.page).toBe(0));
    });

    it('reconciling a dish that is not in the view is a no-op, not an error', async () => {
      vi.mocked(dishReviewApi.getInbox).mockResolvedValue(page({ items: [item('a')] }));
      const { result } = renderHook(() => useDishReviewInbox());
      await waitFor(() => expect(result.current.items).toHaveLength(1));

      act(() => result.current.reconcileAfterStaleDecision('not-here'));

      expect(result.current.items).toHaveLength(1);
      expect(result.current.error).toBeNull();
    });
  });
});
