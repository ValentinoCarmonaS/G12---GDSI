import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AdminDishReviewPage } from '../../src/pages/AdminDishReviewPage';
import { dishReviewApi } from '../../src/api/dishReviewApi';
import type {
  DishReviewDetail,
  DishReviewInboxItem,
  DishReviewInboxPage,
} from '../../src/types/dishReview';

vi.mock('../../src/api/dishReviewApi', () => ({
  dishReviewApi: {
    getInbox: vi.fn(),
    getDetail: vi.fn(),
  },
}));

vi.mock('../../src/components/common/WorkspaceLayout', () => ({
  WorkspaceLayout: ({ title, children }: { title: string; children: React.ReactNode }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

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

function inboxPage(overrides: Partial<DishReviewInboxPage> = {}): DishReviewInboxPage {
  return {
    items: [item()],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    pendingCount: 1,
    ...overrides,
  };
}

function detail(overrides: Partial<DishReviewDetail> = {}): DishReviewDetail {
  return {
    dishId: 'dish-1',
    name: 'Risotto de Hongos',
    description: null,
    category: 'Platos principales',
    status: 'PENDING_PRICE',
    retailPrice: null,
    photoUrl: null,
    authorName: 'Camila Duarte',
    authorDeactivated: false,
    allergensConfirmed: true,
    submittedAt: '2026-10-01T10:00:00Z',
    publishedAt: null,
    reviewCycleCount: 0,
    isResubmission: false,
    margin: {
      foodCost: 4500,
      retailPrice: null,
      marginPct: null,
      marginTolerancePp: null,
      toleranceThresholdPrice: null,
      hasUnpricedIngredients: false,
      marginDerivable: true,
    },
    publicationGate: {
      satisfied: false,
      unmetConditions: ['RETAIL_PRICE_MISSING'],
      requiredAcknowledgements: [],
    },
    recipeAbsent: false,
    foodCostAudit: {
      dishId: 'dish-1',
      dishName: 'Risotto de Hongos',
      totalFoodCost: 4500,
      hasUnpricedIngredients: false,
      warningNotes: [],
      requiredDietaryRestrictions: ['CELIAC'],
      items: [],
    },
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminDishReviewPage />
    </MemoryRouter>
  );
}

describe('AdminDishReviewPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(dishReviewApi.getInbox).mockResolvedValue(inboxPage());
    vi.mocked(dishReviewApi.getDetail).mockResolvedValue(detail());
  });

  describe('the inbox-to-detail transition (FR-002, FR-006)', () => {
    it('loads the inbox on arrival without the Administrator asking', async () => {
      renderPage();

      await waitFor(() => expect(dishReviewApi.getInbox).toHaveBeenCalled());
      expect(await screen.findByTestId('inbox-row')).toBeInTheDocument();
    });

    it('invites the Administrator to pick a dish before one is selected', async () => {
      renderPage();
      await screen.findByTestId('inbox-row');

      expect(screen.getByTestId('detail-placeholder')).toBeInTheDocument();
      expect(dishReviewApi.getDetail).not.toHaveBeenCalled();
    });

    it('opens the detail of the dish that was clicked', async () => {
      renderPage();
      await userEvent.click(await screen.findByTestId('inbox-row'));

      await waitFor(() => expect(dishReviewApi.getDetail).toHaveBeenCalledWith('dish-1'));
      expect(await screen.findByTestId('dietary-labels')).toBeInTheDocument();
    });

    it('keeps the inbox on screen beside the detail, so the queue stays visible', async () => {
      renderPage();
      await userEvent.click(await screen.findByTestId('inbox-row'));
      await screen.findByTestId('dietary-labels');

      expect(screen.getByTestId('inbox-row')).toBeInTheDocument();
    });

    it('switching tabs clears the open detail, which belonged to the previous view', async () => {
      renderPage();
      await userEvent.click(await screen.findByTestId('inbox-row'));
      await screen.findByTestId('dietary-labels');

      await userEvent.click(screen.getByRole('tab', { name: /publicados/i }));

      await waitFor(() => expect(screen.getByTestId('detail-placeholder')).toBeInTheDocument());
    });
  });

  describe('each backend error renders as itself (FR-032)', () => {
    it('a permission failure on the inbox is shown as a non-recoverable notice', async () => {
      vi.mocked(dishReviewApi.getInbox).mockRejectedValue({
        response: { status: 403, data: { detail: 'Acceso denegado.' } },
      });

      renderPage();

      expect(await screen.findByTestId('inbox-error-state')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /reintentar/i })).not.toBeInTheDocument();
    });

    it('a transport failure on the inbox offers a retry that refetches', async () => {
      vi.mocked(dishReviewApi.getInbox).mockRejectedValueOnce({ message: 'Network Error' });

      renderPage();
      const retry = await screen.findByRole('button', { name: /reintentar/i });

      vi.mocked(dishReviewApi.getInbox).mockResolvedValue(inboxPage());
      await userEvent.click(retry);

      expect(await screen.findByTestId('inbox-row')).toBeInTheDocument();
    });

    it('a 404 on the detail is shown without destroying the inbox', async () => {
      vi.mocked(dishReviewApi.getDetail).mockRejectedValue({ response: { status: 404 } });

      renderPage();
      await userEvent.click(await screen.findByTestId('inbox-row'));

      expect(await screen.findByTestId('detail-error')).toHaveTextContent(/no (se )?encontr/i);
      expect(screen.getByTestId('inbox-row'))
        .toBeInTheDocument();
    });

    it('a detail failure does not leave the previous dish on screen', async () => {
      renderPage();
      await userEvent.click(await screen.findByTestId('inbox-row'));
      await screen.findByTestId('dietary-labels');

      vi.mocked(dishReviewApi.getDetail).mockRejectedValue({ response: { status: 500 } });
      await userEvent.click(screen.getByTestId('inbox-row'));

      // Leaving the old panel up beside an error would have the Administrator decide on a
      // dish they are no longer looking at.
      await waitFor(() => expect(screen.getByTestId('detail-error')).toBeInTheDocument());
      expect(screen.queryByTestId('dietary-labels')).not.toBeInTheDocument();
    });
  });

  describe('loading states are distinct from empty ones (FR-029)', () => {
    it('shows a skeleton while the inbox is in flight', () => {
      vi.mocked(dishReviewApi.getInbox).mockReturnValue(new Promise(() => {}));

      renderPage();

      expect(screen.getByTestId('inbox-loading-skeleton')).toBeInTheDocument();
      expect(screen.queryByTestId('inbox-empty-state')).not.toBeInTheDocument();
    });

    it('shows a detail loading state rather than an empty panel', async () => {
      renderPage();
      vi.mocked(dishReviewApi.getDetail).mockReturnValue(new Promise(() => {}));

      await userEvent.click(await screen.findByTestId('inbox-row'));

      expect(await screen.findByTestId('detail-loading')).toBeInTheDocument();
    });

    it('an empty inbox explains itself and no detail is attempted', async () => {
      vi.mocked(dishReviewApi.getInbox).mockResolvedValue(
        inboxPage({ items: [], totalElements: 0, totalPages: 0, pendingCount: 0 })
      );

      renderPage();

      expect(await screen.findByTestId('inbox-empty-state')).toBeInTheDocument();
      expect(dishReviewApi.getDetail).not.toHaveBeenCalled();
    });
  });

  describe('the information the detail must carry (FR-006, SC-007)', () => {
    it('renders the five blocks SC-007 requires for a sufficient decision', async () => {
      renderPage();
      await userEvent.click(await screen.findByTestId('inbox-row'));

      // identity, labels, itemized cost, gate status, author attribution
      expect(await screen.findByRole('heading', { name: /risotto de hongos/i }))
        .toBeInTheDocument();
      expect(screen.getByTestId('dietary-labels')).toBeInTheDocument();
      expect(screen.getByTestId('food-cost-total')).toBeInTheDocument();
      expect(screen.getByTestId('gate-status')).toBeInTheDocument();
      expect(screen.getByTestId('audit-author')).toBeInTheDocument();
    });

    it('a dish with no recipe still renders its file (FR-037)', async () => {
      vi.mocked(dishReviewApi.getDetail).mockResolvedValue(
        detail({ recipeAbsent: true, foodCostAudit: null })
      );

      renderPage();
      await userEvent.click(await screen.findByTestId('inbox-row'));

      expect(await screen.findByTestId('recipe-absent-state')).toBeInTheDocument();
      expect(screen.getByTestId('audit-author')).toBeInTheDocument();
    });
  });
});
