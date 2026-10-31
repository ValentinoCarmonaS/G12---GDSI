import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { DishCostAuditPage } from '@/pages/DishCostAuditPage';
import { AdminWorkspacePage } from '@/pages/AdminWorkspacePage';
import { dishApi } from '@/api/dishApi';
import { adminNotificationApi } from '@/api/adminNotificationApi';
import { staffApi } from '@/api/staffApi';
import { inventoryApi } from '@/api/inventoryApi';
import { authApi } from '@/api/authApi';
import * as AuthModule from '@/hooks/useAuth';
import type { FoodCostAuditResponse } from '@/types/dish';

vi.mock('@/api/dishApi', () => ({
  dishApi: {
    getFoodCostAudit: vi.fn(),
  },
}));

vi.mock('@/api/adminNotificationApi', () => ({
  adminNotificationApi: {
    listNotifications: vi.fn(),
  },
}));

vi.mock('@/api/staffApi', () => ({
  staffApi: {
    listStaff: vi.fn(),
  },
}));

vi.mock('@/api/inventoryApi', () => ({
  inventoryApi: {
    listIngredients: vi.fn(),
  },
}));

vi.mock('@/hooks/useAuth');

describe('User Story ADM-5.1.2 Integration Test - Auditoría de Receta y Food Cost', () => {
  const dishId = 'd1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01';

  const mockSuccessfulAudit: FoodCostAuditResponse = {
    dishId,
    dishName: 'Risotto de Hongos',
    totalFoodCost: 3500.50,
    hasUnpricedIngredients: false,
    warningNotes: [],
    requiredDietaryRestrictions: ['CELIAC', 'VEGETARIAN'],
    items: [
      {
        ingredientId: 'i1',
        ingredientName: 'Arroz Carnaroli',
        recipeQuantity: 0.25,
        unitOfMeasure: 'KG',
        unitPurchaseCost: 6000.0,
        lineSubtotal: 1500.0,
        unpricedWarning: false,
      },
      {
        ingredientId: 'i2',
        ingredientName: 'Hongos Porcini',
        recipeQuantity: 0.1,
        unitOfMeasure: 'KG',
        unitPurchaseCost: 20005.0,
        lineSubtotal: 2000.50,
        unpricedWarning: false,
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined as any);
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'admin-user-1',
        email: 'admin@easyeat.com',
        role: 'ADMINISTRADOR',
        tenantId: 'tenant-test',
        tokenVersion: 1,
      },
      token: 'jwt-admin-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('renders loading state initially and then shows complete food cost audit with ingredients, subtotals, and dietary labels', async () => {
    vi.mocked(dishApi.getFoodCostAudit).mockResolvedValue(mockSuccessfulAudit);

    render(
      <MemoryRouter initialEntries={[`/admin/dishes/${dishId}/cost-audit`]}>
        <Routes>
          <Route path="/admin/dishes/:id/cost-audit" element={<DishCostAuditPage />} />
        </Routes>
      </MemoryRouter>
    );

    // Initial loading indicator
    expect(screen.getByText(/Calculando costos y consultando catálogo/i)).toBeInTheDocument();

    // After resolution
    await waitFor(() => {
      expect(screen.getByText('Risotto de Hongos')).toBeInTheDocument();
    });

    expect(screen.getByText('Arroz Carnaroli')).toBeInTheDocument();
    expect(screen.getByText('Hongos Porcini')).toBeInTheDocument();
    expect(screen.getByText('$3500.50')).toBeInTheDocument();

    // Dietary labels
    expect(screen.getByText('CELIAC')).toBeInTheDocument();
    expect(screen.getByText('VEGETARIAN')).toBeInTheDocument();

    // API called with the right path parameter
    expect(dishApi.getFoodCostAudit).toHaveBeenCalledWith(dishId);
  });

  it('displays warning alert and flags when ingredients lack acquisition purchase cost (hasUnpricedIngredients: true)', async () => {
    const unpricedAudit: FoodCostAuditResponse = {
      dishId,
      dishName: 'Risotto con Trufa Especial',
      totalFoodCost: 1500.0,
      hasUnpricedIngredients: true,
      warningNotes: ['Insumo sin precio de adquisición: Trufa Fresca'],
      items: [
        {
          ingredientId: 'i1',
          ingredientName: 'Arroz Carnaroli',
          recipeQuantity: 0.25,
          unitOfMeasure: 'KG',
          unitPurchaseCost: 6000.0,
          lineSubtotal: 1500.0,
          unpricedWarning: false,
        },
        {
          ingredientId: 'i3',
          ingredientName: 'Trufa Fresca',
          recipeQuantity: 0.02,
          unitOfMeasure: 'KG',
          unitPurchaseCost: 0,
          lineSubtotal: 0,
          unpricedWarning: true,
        },
      ],
      requiredDietaryRestrictions: [],
    };

    vi.mocked(dishApi.getFoodCostAudit).mockResolvedValue(unpricedAudit);

    render(
      <MemoryRouter initialEntries={[`/admin/dishes/${dishId}/cost-audit`]}>
        <Routes>
          <Route path="/admin/dishes/:id/cost-audit" element={<DishCostAuditPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Risotto con Trufa Especial')).toBeInTheDocument();
    });

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/Advertencia de Insumos sin Precio/i)).toBeInTheDocument();
    expect(screen.getByText(/Insumo sin precio de adquisición: Trufa Fresca/i)).toBeInTheDocument();
    expect(screen.getByText('Sin costo')).toBeInTheDocument();
  });

  it('displays state gate rejection error message when backend responds with 409 Conflict', async () => {
    const conflictError: any = new Error('Conflict');
    conflictError.response = { status: 409 };
    vi.mocked(dishApi.getFoodCostAudit).mockRejectedValue(conflictError);

    render(
      <MemoryRouter initialEntries={[`/admin/dishes/${dishId}/cost-audit`]}>
        <Routes>
          <Route path="/admin/dishes/:id/cost-audit" element={<DishCostAuditPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/El plato debe estar en estado PENDING_PRICE/i)).toBeInTheDocument();
    });
  });

  it('displays empty recipe error message when backend responds with 400 Bad Request', async () => {
    const badRequestError: any = new Error('Bad Request');
    badRequestError.response = { status: 400 };
    vi.mocked(dishApi.getFoodCostAudit).mockRejectedValue(badRequestError);

    render(
      <MemoryRouter initialEntries={[`/admin/dishes/${dishId}/cost-audit`]}>
        <Routes>
          <Route path="/admin/dishes/:id/cost-audit" element={<DishCostAuditPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/El plato no tiene ingredientes asignados/i)).toBeInTheDocument();
    });
  });

  it('navigates from AdminWorkspacePage pending notification to DishCostAuditPage when clicking Auditar Costo', async () => {
    vi.mocked(staffApi.listStaff).mockResolvedValue([]);
    vi.mocked(inventoryApi.listIngredients).mockResolvedValue([]);
    vi.mocked(adminNotificationApi.listNotifications).mockResolvedValue({
      content: [
        {
          id: 'notif-1',
          dishId,
          title: 'Plato enviado a revisión',
          message: 'Chef envió Risotto de Hongos a revisión',
          read: false,
          createdAt: '2026-10-28T12:00:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 10,
      unreadCount: 1,
    } as any);

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/admin" element={<AdminWorkspacePage />} />
          <Route path="/admin/dishes/:id/cost-audit" element={<DishCostAuditPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Plato enviado a revisión')).toBeInTheDocument();
    });

    const auditBtn = screen.getByTestId(`audit-dish-btn-${dishId}`);
    expect(auditBtn).toBeInTheDocument();
    expect(auditBtn).toHaveAttribute('href', `/admin/dishes/${dishId}/cost-audit`);
  });
});
