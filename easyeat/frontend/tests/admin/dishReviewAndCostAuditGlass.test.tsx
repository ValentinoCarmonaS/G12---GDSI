import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AdminDishReviewPage } from '@/pages/AdminDishReviewPage';
import { DishCostAuditPage } from '@/pages/DishCostAuditPage';
import {
  AdminPendingDishesProvider,
  useAdminPendingDishes,
} from '@/context/AdminPendingDishesContext';
import { dishReviewApi } from '@/api/dishReviewApi';
import { dishApi } from '@/api/dishApi';
import type {
  DishReviewDetail,
  DishReviewInboxItem,
  DishReviewInboxPage,
} from '@/types/dishReview';
import type { FoodCostAuditResponse } from '@/types/dish';

vi.mock('@/api/dishReviewApi', () => ({
  dishReviewApi: {
    getInbox: vi.fn(),
    getDetail: vi.fn(),
    setRetailPrice: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
    cancel: vi.fn(),
  },
}));

vi.mock('@/api/dishApi', () => ({
  dishApi: {
    getFoodCostAudit: vi.fn(),
  },
}));

vi.mock('@/components/common/WorkspaceLayout', () => ({
  WorkspaceLayout: ({ title, children }: { title: string; children: React.ReactNode }) => (
    <div data-testid="workspace-layout">
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

function makeInboxItem(overrides: Partial<DishReviewInboxItem> = {}): DishReviewInboxItem {
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

function makeInboxPage(overrides: Partial<DishReviewInboxPage> = {}): DishReviewInboxPage {
  return {
    items: [makeInboxItem()],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 2,
    hasNext: true,
    pendingCount: 2,
    ...overrides,
  };
}

function makeDetail(overrides: Partial<DishReviewDetail> = {}): DishReviewDetail {
  return {
    dishId: 'dish-1',
    name: 'Risotto de Hongos',
    description: 'Arroz carnaroli con hongos de pino.',
    category: 'Platos principales',
    status: 'PENDING_PRICE',
    retailPrice: 4000,
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
      retailPrice: 4000,
      marginPct: -12.5,
      marginTolerancePp: null,
      toleranceThresholdPrice: null,
      hasUnpricedIngredients: true,
      marginDerivable: true,
    },
    publicationGate: {
      satisfied: true,
      unmetConditions: [],
      requiredAcknowledgements: ['UNPRICED_INGREDIENTS', 'NEGATIVE_MARGIN'],
    },
    recipeAbsent: false,
    foodCostAudit: {
      dishId: 'dish-1',
      dishName: 'Risotto de Hongos',
      totalFoodCost: 4500,
      hasUnpricedIngredients: true,
      warningNotes: ['Insumo sin precio: Caldo de vegetales'],
      requiredDietaryRestrictions: ['CELIAC'],
      items: [
        {
          ingredientId: 'ing-1',
          ingredientName: 'Arroz Carnaroli',
          recipeQuantity: 0.25,
          unitOfMeasure: 'KG',
          unitPurchaseCost: 6000,
          lineSubtotal: 1500,
          unpricedWarning: false,
        },
        {
          ingredientId: 'ing-2',
          ingredientName: 'Caldo de vegetales',
          recipeQuantity: 0.5,
          unitOfMeasure: 'L',
          unitPurchaseCost: 0,
          lineSubtotal: 0,
          unpricedWarning: true,
        },
      ],
    },
    ...overrides,
  };
}

const PendingCountObserver: React.FC = () => {
  const { pendingCount } = useAdminPendingDishes();
  return <span data-testid="context-pending-count">{pendingCount ?? 'null'}</span>;
};

function renderReviewPageWithContext() {
  return render(
    <MemoryRouter initialEntries={['/admin/dishes']}>
      <AdminPendingDishesProvider>
        <PendingCountObserver />
        <AdminDishReviewPage />
      </AdminPendingDishesProvider>
    </MemoryRouter>
  );
}

describe('Spec 026 US3 — Bandeja de Revisión y Auditoría de Costos Glassmorphism', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(dishReviewApi.getInbox).mockResolvedValue(makeInboxPage());
    vi.mocked(dishReviewApi.getDetail).mockResolvedValue(makeDetail());
  });

  it('renders desktop split-pane Glassmorphism layout and mobile master-detail flow with Volver a la bandeja (>= 44x44px) preserving tab and page', async () => {
    const user = userEvent.setup();
    renderReviewPageWithContext();

    const row = await screen.findByTestId('inbox-row');
    const splitGrid = screen.getByTestId('dish-review-split-pane');
    expect(splitGrid.className).toContain('lg:grid-cols-[380px_minmax(0,1fr)]');
    expect(splitGrid.innerHTML).not.toContain('text-purple-');

    // Navigate to page 2 before selecting a dish
    await user.click(screen.getByRole('button', { name: /siguiente/i }));
    await waitFor(() =>
      expect(dishReviewApi.getInbox).toHaveBeenCalledWith(
        expect.objectContaining({ tab: 'PENDING', page: 1 })
      )
    );

    await user.click(row);
    await screen.findByTestId('dietary-labels');

    const backButton = screen.getByRole('button', { name: /volver a la bandeja/i });
    expect(backButton).toBeInTheDocument();
    expect(backButton.className).toContain('min-h-[44px]');
    expect(backButton.className).toContain('min-w-[44px]');

    await user.click(backButton);
    expect(screen.getByTestId('detail-placeholder')).toBeInTheDocument();
    // Tab PENDING remains selected
    expect(screen.getByRole('tab', { name: /en espera/i })).toHaveAttribute('aria-selected', 'true');
  });

  it('renders ReviewInboxRow and FoodCostBreakdown in GlassCard cards with JetBrains Mono figures and no legacy table', async () => {
    const user = userEvent.setup();
    renderReviewPageWithContext();

    const row = await screen.findByTestId('inbox-row');
    expect(row.className).toContain('backdrop-blur-[12px]');

    await user.click(row);
    const totalEl = await screen.findByTestId('food-cost-total');
    expect(totalEl.className).toContain('font-ee-mono');
    expect(totalEl.className).toContain('tabular-nums');

    const costLines = screen.getAllByTestId('cost-line');
    expect(costLines.length).toBe(2);
    expect(costLines[0].tagName.toLowerCase()).not.toBe('tr');
    expect(costLines[0].className).toContain('backdrop-blur-[12px]');
  });

  it('closes PublicationConfirmModal and RejectionDialog via Escape using AccessibleModal', async () => {
    const user = userEvent.setup();
    renderReviewPageWithContext();

    await user.click(await screen.findByTestId('inbox-row'));
    await screen.findByTestId('dietary-labels');

    // Open PublicationConfirmModal
    await user.click(screen.getByRole('button', { name: /aprobar y publicar/i }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // Open RejectionDialog
    await user.click(screen.getByRole('button', { name: /rechazar \/ devolver a cocina/i }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('synchronizes pendingCount in AdminPendingDishesContext immediately after approving, rejecting, or cancelling', async () => {
    const user = userEvent.setup();
    vi.mocked(dishReviewApi.approve).mockResolvedValue({
      dishId: 'dish-1',
      status: 'PUBLISHED',
      alreadyPublished: false,
      publishedAt: '2026-10-05T10:00:00Z',
      reviewHistoryEntryId: 'h-1',
    });
    vi.mocked(dishReviewApi.cancel).mockResolvedValue(undefined);

    renderReviewPageWithContext();
    await waitFor(() =>
      expect(screen.getByTestId('context-pending-count')).toHaveTextContent('2')
    );

    await user.click(await screen.findByTestId('inbox-row'));
    await screen.findByTestId('dietary-labels');

    // Approve dish -> pendingCount decrements immediately
    vi.mocked(dishReviewApi.getInbox).mockResolvedValue(makeInboxPage({ pendingCount: 1, totalElements: 1 }));
    await user.click(screen.getByRole('button', { name: /aprobar y publicar/i }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: /confirmar/i }));

    await waitFor(() =>
      expect(screen.getByTestId('context-pending-count')).toHaveTextContent('1')
    );
  });

  it('renders DishCostAuditPage in GlassCard cards with JetBrains Mono figures and no purple classes or legacy table', async () => {
    const auditResponse: FoodCostAuditResponse = {
      dishId: 'dish-9',
      dishName: 'Pizza Napolitana',
      totalFoodCost: 2050.0,
      hasUnpricedIngredients: true,
      warningNotes: ['Insumo sin precio: Albahaca'],
      requiredDietaryRestrictions: ['CELIAC'],
      items: [
        {
          ingredientId: 'i-1',
          ingredientName: 'Harina Sin Gluten',
          recipeQuantity: 0.3,
          unitOfMeasure: 'KG',
          unitPurchaseCost: 1500.0,
          lineSubtotal: 450.0,
          unpricedWarning: false,
        },
        {
          ingredientId: 'i-2',
          ingredientName: 'Albahaca',
          recipeQuantity: 0.05,
          unitOfMeasure: 'KG',
          unitPurchaseCost: 0,
          lineSubtotal: 0,
          unpricedWarning: true,
        },
      ],
    };
    vi.mocked(dishApi.getFoodCostAudit).mockResolvedValue(auditResponse);

    const { container } = render(
      <MemoryRouter initialEntries={['/admin/dishes/dish-9/cost-audit']}>
        <Routes>
          <Route path="/admin/dishes/:id/cost-audit" element={<DishCostAuditPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('Pizza Napolitana')).toBeInTheDocument();
    expect(screen.getByText('$2050.00')).toBeInTheDocument();
    expect(container.querySelector('table')).toBeNull();
    expect(container.innerHTML).not.toContain('text-purple-');
    expect(container.innerHTML).toContain('backdrop-blur-[12px]');
    expect(container.innerHTML).toContain('font-ee-mono');
  });
});
