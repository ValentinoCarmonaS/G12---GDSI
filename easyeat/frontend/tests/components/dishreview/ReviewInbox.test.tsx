import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReviewInboxTabs } from '../../../src/components/dishreview/ReviewInboxTabs';
import { ReviewInboxList } from '../../../src/components/dishreview/ReviewInboxList';
import { InboxPagination } from '../../../src/components/dishreview/InboxPagination';
import type { DishReviewInboxItem, InboxTab } from '../../../src/types/dishReview';

/**
 * The inbox as the Administrator meets it: tabs, list and pagination rendered together.
 *
 * There is no `ReviewInbox.tsx`. Neither `plan.md`'s component tree nor `tasks.md` declares one,
 * and inventing a composite to match this file's name would add an artifact no document asks for
 * (Principle V). The file is named after the feature; the subject is the four components
 * behaving as one surface, which is the only place tab switching, FIFO order, pagination and the
 * badge can be checked against each other.
 */

function item(overrides: Partial<DishReviewInboxItem> = {}): DishReviewInboxItem {
  return {
    dishId: 'dish-1',
    name: 'Risotto de Hongos',
    category: 'Platos principales',
    status: 'PENDING_PRICE',
    authorName: 'Camila Duarte',
    authorDeactivated: false,
    submittedAt: '2026-10-01T10:00:00Z',
    publishedAt: null,
    retailPrice: null,
    isResubmission: false,
    reviewCycleCount: 0,
    ...overrides,
  };
}

function renderInbox(options: {
  tab?: InboxTab;
  items?: DishReviewInboxItem[];
  pendingCount?: number;
  page?: number;
  totalPages?: number;
  hasNext?: boolean;
  isLoading?: boolean;
  error?: { message: string; retryable: boolean } | null;
  onTabChange?: (tab: InboxTab) => void;
  onPageChange?: (page: number) => void;
  onSelect?: (dishId: string) => void;
  onRetry?: () => void;
} = {}) {
  const {
    tab = 'PENDING',
    items = [],
    pendingCount = 0,
    page = 0,
    totalPages = 1,
    hasNext = false,
    isLoading = false,
    error = null,
    onTabChange = vi.fn(),
    onPageChange = vi.fn(),
    onSelect = vi.fn(),
    onRetry = vi.fn(),
  } = options;

  render(
    <div>
      <ReviewInboxTabs activeTab={tab} pendingCount={pendingCount} onTabChange={onTabChange} />
      <ReviewInboxList
        items={items}
        tab={tab}
        isLoading={isLoading}
        error={error}
        onSelect={onSelect}
        onRetry={onRetry}
      />
      <InboxPagination
        page={page}
        totalPages={totalPages}
        hasNext={hasNext}
        onPageChange={onPageChange}
      />
    </div>
  );

  return { onTabChange, onPageChange, onSelect, onRetry };
}

describe('the review inbox surface', () => {
  describe('the three tabs and the badge (FR-002, FR-003)', () => {
    it('offers exactly the three views FR-002 defines', () => {
      renderInbox();

      expect(screen.getByRole('tab', { name: /pendientes/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /publicados/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /historial/i })).toBeInTheDocument();
      expect(screen.getAllByRole('tab')).toHaveLength(3);
    });

    it('marks the active tab for assistive technology, not only visually', () => {
      renderInbox({ tab: 'PUBLISHED' });

      expect(screen.getByRole('tab', { name: /publicados/i })).toHaveAttribute(
        'aria-selected',
        'true'
      );
      expect(screen.getByRole('tab', { name: /pendientes/i })).toHaveAttribute(
        'aria-selected',
        'false'
      );
    });

    it('reports the tab the Administrator chose', async () => {
      const { onTabChange } = renderInbox();

      await userEvent.click(screen.getByRole('tab', { name: /historial/i }));

      expect(onTabChange).toHaveBeenCalledWith('HISTORY');
    });

    it('shows the pending badge on every tab, including the published one', () => {
      renderInbox({ tab: 'PUBLISHED', pendingCount: 37 });

      expect(screen.getByTestId('pending-count-badge')).toHaveTextContent('37');
    });

    it('hides the badge at zero rather than showing a zero that reads as a problem', () => {
      renderInbox({ pendingCount: 0 });

      expect(screen.queryByTestId('pending-count-badge')).not.toBeInTheDocument();
    });
  });

  describe('the list in FIFO order (FR-003, FR-004)', () => {
    it('renders rows in the order given, without re-sorting them', () => {
      renderInbox({
        items: [
          item({ dishId: 'a', name: 'Alfajor', submittedAt: '2026-10-01T10:00:00Z' }),
          item({ dishId: 'b', name: 'Budin', submittedAt: '2026-10-02T10:00:00Z' }),
          item({ dishId: 'c', name: 'Canelones', submittedAt: '2026-10-03T10:00:00Z' }),
        ],
      });

      const rendered = screen.getAllByTestId('inbox-row').map((row) => row.textContent);
      expect(rendered[0]).toContain('Alfajor');
      expect(rendered[2]).toContain('Canelones');
    });

    it('shows every field FR-004 names on a row', () => {
      renderInbox({ items: [item()] });

      const row = screen.getByTestId('inbox-row');
      expect(row).toHaveTextContent('Risotto de Hongos');
      expect(row).toHaveTextContent('Platos principales');
      expect(row).toHaveTextContent('Camila Duarte');
      expect(screen.getByTestId('dish-status-badge')).toBeInTheDocument();
      expect(screen.getByTestId('inbox-row-submitted-at')).toBeInTheDocument();
    });

    it('distinguishes an initial submission from a resubmission', () => {
      renderInbox({ items: [item({ isResubmission: false })] });
      expect(screen.getByTestId('resubmission-indicator')).toHaveTextContent(/inicial/i);
    });

    it('labels a resubmission with its cycle, so the count is visible not implied', () => {
      renderInbox({ items: [item({ isResubmission: true, reviewCycleCount: 2 })] });

      const indicator = screen.getByTestId('resubmission-indicator');
      expect(indicator).toHaveTextContent(/reenv/i);
      expect(indicator).toHaveTextContent('2');
    });

    it('renders an unattributed dish as "Autor no registrado" (FR-027)', () => {
      renderInbox({ items: [item({ authorName: null })] });

      expect(screen.getByTestId('inbox-row')).toHaveTextContent(/autor no registrado/i);
    });

    it('renders a row with no submission instant as "Sin fecha de envío"', () => {
      renderInbox({ items: [item({ submittedAt: null })] });

      // The contract allows a null submittedAt on the PUBLISHED and HISTORY tabs. A blank cell
      // there is indistinguishable from a formatting fault, which FR-027 rules out for the
      // author column and which is no more readable here.
      expect(screen.getByTestId('inbox-row-submitted-at')).toHaveTextContent(/sin fecha de envío/i);
      expect(screen.getByTestId('inbox-row')).toHaveTextContent('Risotto de Hongos');
    });

    it('marks a suspended author without implying the dish is blocked (FR-026)', () => {
      renderInbox({ items: [item({ authorDeactivated: true })] });

      const row = screen.getByTestId('inbox-row');
      expect(row).toHaveTextContent(/camila duarte/i);
      expect(screen.getByTestId('author-deactivated-badge')).toBeInTheDocument();
      expect(row).not.toHaveAttribute('aria-disabled', 'true');
    });

    it('reports the dish the Administrator opened', async () => {
      const { onSelect } = renderInbox({ items: [item({ dishId: 'dish-42' })] });

      await userEvent.click(screen.getByTestId('inbox-row'));

      expect(onSelect).toHaveBeenCalledWith('dish-42');
    });
  });

  describe('empty, loading and error are three different states (FR-005, FR-029, FR-032)', () => {
    it('an empty pending view explains itself rather than showing a blank panel', () => {
      renderInbox({ tab: 'PENDING', items: [] });

      expect(screen.getByTestId('inbox-empty-state')).toBeInTheDocument();
      expect(screen.queryByTestId('inbox-row')).not.toBeInTheDocument();
    });

    it('each view gets its own empty wording, since they mean different things', () => {
      const { unmount } = render(
        <ReviewInboxList items={[]} tab="PENDING" isLoading={false} error={null}
          onSelect={vi.fn()} onRetry={vi.fn()} />
      );
      const pendingText = screen.getByTestId('inbox-empty-state').textContent;
      unmount();

      render(
        <ReviewInboxList items={[]} tab="HISTORY" isLoading={false} error={null}
          onSelect={vi.fn()} onRetry={vi.fn()} />
      );
      const historyText = screen.getByTestId('inbox-empty-state').textContent;

      expect(historyText).not.toEqual(pendingText);
    });

    it('loading shows a skeleton, not the empty state: they say opposite things', () => {
      renderInbox({ isLoading: true, items: [] });

      expect(screen.getByTestId('inbox-loading-skeleton')).toBeInTheDocument();
      expect(screen.queryByTestId('inbox-empty-state')).not.toBeInTheDocument();
    });

    it('a retryable error offers a retry and reports it when used', async () => {
      const { onRetry } = renderInbox({
        error: { message: 'No se pudo conectar con el servidor.', retryable: true },
      });

      const retry = screen.getByRole('button', { name: /reintentar/i });
      await userEvent.click(retry);

      expect(onRetry).toHaveBeenCalled();
    });

    it('a non-retryable error offers no retry, because retrying cannot help', () => {
      renderInbox({
        error: { message: 'No tenés permiso para ver la bandeja.', retryable: false },
      });

      expect(screen.getByTestId('inbox-error-state')).toHaveTextContent(/permiso/i);
      expect(screen.queryByRole('button', { name: /reintentar/i })).not.toBeInTheDocument();
    });

    it('an error replaces the list rather than sitting above stale rows', () => {
      renderInbox({
        items: [item()],
        error: { message: 'Error', retryable: true },
      });

      expect(screen.queryByTestId('inbox-row')).not.toBeInTheDocument();
    });
  });

  describe('pagination, 20 per page (FR-003, FR-036)', () => {
    it('is hidden when everything fits on one page', () => {
      renderInbox({ totalPages: 1 });

      expect(screen.queryByTestId('inbox-pagination')).not.toBeInTheDocument();
    });

    it('states the position in human terms, counting from one', () => {
      renderInbox({ page: 1, totalPages: 3, hasNext: true });

      expect(screen.getByTestId('inbox-pagination')).toHaveTextContent('2');
      expect(screen.getByTestId('inbox-pagination')).toHaveTextContent('3');
    });

    it('moves forward and back by one page', async () => {
      const { onPageChange } = renderInbox({ page: 1, totalPages: 3, hasNext: true });

      await userEvent.click(screen.getByRole('button', { name: /siguiente/i }));
      expect(onPageChange).toHaveBeenCalledWith(2);

      await userEvent.click(screen.getByRole('button', { name: /anterior/i }));
      expect(onPageChange).toHaveBeenCalledWith(0);
    });

    it('disables the controls at the ends instead of allowing a page that does not exist', () => {
      const { unmount } = render(
        <InboxPagination page={0} totalPages={3} hasNext onPageChange={vi.fn()} />
      );
      expect(screen.getByRole('button', { name: /anterior/i })).toBeDisabled();
      unmount();

      render(<InboxPagination page={2} totalPages={3} hasNext={false} onPageChange={vi.fn()} />);
      expect(screen.getByRole('button', { name: /siguiente/i })).toBeDisabled();
    });
  });
});
