import React from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GlassCard } from '@/components/admin/GlassCard';
import {
  AdminPendingDishesProvider,
  useAdminPendingDishes,
} from '@/context/AdminPendingDishesContext';
import { dishReviewApi } from '@/api/dishReviewApi';

vi.mock('@/api/dishReviewApi', () => ({
  dishReviewApi: {
    getInbox: vi.fn(),
  },
}));

const mockedGetInbox = vi.mocked(dishReviewApi.getInbox);

const PendingConsumer: React.FC = () => {
  const { pendingCount, setPendingCount, decrementPendingCount, refreshPendingCount } =
    useAdminPendingDishes();

  return (
    <div>
      <span data-testid="pending-count">
        {pendingCount === null ? 'null' : String(pendingCount)}
      </span>
      <button type="button" onClick={() => setPendingCount(5)}>
        set-5
      </button>
      <button type="button" onClick={() => decrementPendingCount()}>
        decrement
      </button>
      <button type="button" onClick={() => void refreshPendingCount()}>
        refresh
      </button>
    </div>
  );
};

describe('Phase 2 Foundational — GlassCard & AdminPendingDishesContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('GlassCard', () => {
    it('renders elevated, subtle, interactive, and accent variants with 1px border and backdrop blur', () => {
      const { rerender } = render(
        <GlassCard data-testid="glass-card" variant="elevated">
          Elevated
        </GlassCard>
      );
      const card = screen.getByTestId('glass-card');
      expect(card.className).toContain('border');
      expect(card.className).toContain('backdrop-blur-[12px]');

      rerender(
        <GlassCard data-testid="glass-card" variant="subtle">
          Subtle
        </GlassCard>
      );
      expect(screen.getByTestId('glass-card').className).toContain('bg-ee-surface-subtle');

      rerender(
        <GlassCard data-testid="glass-card" variant="interactive">
          Interactive
        </GlassCard>
      );
      const interactiveClass = screen.getByTestId('glass-card').className;
      expect(interactiveClass).toContain('duration-150');
      expect(interactiveClass).toContain('motion-reduce:transition-none');

      rerender(
        <GlassCard data-testid="glass-card" variant="accent">
          Accent
        </GlassCard>
      );
      expect(screen.getByTestId('glass-card').className).toContain('border-ee-primary');
    });

    it('supports --ee-* padding and radius tokens and solid fallback for reduced-transparency', () => {
      render(
        <GlassCard data-testid="glass-card" padding="lg" radius="sm" as="section">
          Content
        </GlassCard>
      );
      const card = screen.getByTestId('glass-card');
      expect(card.tagName).toBe('SECTION');
      expect(card.className).toContain('p-8');
      expect(card.className).toContain('rounded-ee-sm');
      expect(card.className).toContain('[@media(prefers-reduced-transparency:reduce)]:bg-ee-surface-elevated');
      expect(card.className).toContain('[@media(prefers-reduced-transparency:reduce)]:backdrop-blur-none');
    });
  });

  describe('AdminPendingDishesProvider', () => {
    it('fetches minimal inbox (size=1) once on mount without polling', async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      mockedGetInbox.mockResolvedValueOnce({
        items: [],
        page: 0,
        size: 1,
        totalElements: 3,
        totalPages: 3,
        hasNext: true,
        pendingCount: 3,
      });

      render(
        <AdminPendingDishesProvider>
          <PendingConsumer />
        </AdminPendingDishesProvider>
      );

      expect(screen.getByTestId('pending-count')).toHaveTextContent('null');

      await waitFor(() => {
        expect(screen.getByTestId('pending-count')).toHaveTextContent('3');
      });

      expect(mockedGetInbox).toHaveBeenCalledTimes(1);
      expect(mockedGetInbox).toHaveBeenCalledWith({ tab: 'PENDING', page: 0, size: 1 });

      await act(async () => {
        vi.advanceTimersByTime(60_000);
      });
      expect(mockedGetInbox).toHaveBeenCalledTimes(1);
    });

    it('keeps pendingCount === null on fetch error (no false zero)', async () => {
      mockedGetInbox.mockRejectedValueOnce(new Error('Network error'));

      render(
        <AdminPendingDishesProvider>
          <PendingConsumer />
        </AdminPendingDishesProvider>
      );

      await waitFor(() => {
        expect(mockedGetInbox).toHaveBeenCalledTimes(1);
      });
      expect(screen.getByTestId('pending-count')).toHaveTextContent('null');
    });

    it('synchronizes setPendingCount and decrementPendingCount in memory without extra requests', async () => {
      const user = userEvent.setup();
      mockedGetInbox.mockResolvedValueOnce({
        items: [],
        page: 0,
        size: 1,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
        pendingCount: 1,
      });

      render(
        <AdminPendingDishesProvider>
          <PendingConsumer />
        </AdminPendingDishesProvider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('pending-count')).toHaveTextContent('1');
      });

      await user.click(screen.getByText('set-5'));
      expect(screen.getByTestId('pending-count')).toHaveTextContent('5');

      await user.click(screen.getByText('decrement'));
      expect(screen.getByTestId('pending-count')).toHaveTextContent('4');

      expect(mockedGetInbox).toHaveBeenCalledTimes(1);
    });
  });
});
