import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import React from 'react';
import { ChefDishesPage } from '../../src/pages/ChefDishesPage';
import { dishApi } from '@/api/dishApi';

vi.mock('@/api/dishApi', () => ({
  dishApi: {
    listDishes: vi.fn(),
    toggleChefPause: vi.fn(),
    getOperationalStatus: vi.fn(),
  },
}));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'chef-1', name: 'Chef Mario', role: 'CHEF' },
    isAuthenticated: true,
    logout: vi.fn(),
  }),
}));

describe('ChefDishesPage (T041, EC-016)', () => {
  const mockDishes = [
    {
      id: 'dish-1',
      name: 'Milanesa Napolitana',
      category: 'Platos principales',
      status: 'PUBLISHED',
      isActive: true,
      pausedByChef: false,
      photoCount: 1,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    {
      id: 'dish-2',
      name: 'Flan con Dulce',
      category: 'Postres',
      status: 'PUBLISHED',
      isActive: true,
      pausedByChef: true,
      photoCount: 1,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(dishApi.listDishes).mockResolvedValue({
      content: mockDishes,
      totalPages: 1,
      totalElements: 2,
      size: 20,
      number: 0,
    } as any);
  });

  it('testToggleRenderedPerDish: renderiza un control de alternancia (toggle) por plato', async () => {
    render(
      <MemoryRouter>
        <ChefDishesPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Milanesa Napolitana')).toBeInTheDocument();
      expect(screen.getByText('Flan con Dulce')).toBeInTheDocument();
    });

    const toggles = screen.getAllByRole('switch', { name: /pausa/i });
    expect(toggles).toHaveLength(2);
  });

  it('testIndividualTogglesOnly: no existen botones de accion masiva o por categoria (EC-016)', async () => {
    render(
      <MemoryRouter>
        <ChefDishesPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Milanesa Napolitana')).toBeInTheDocument();
    });

    expect(screen.queryByText(/pausar todos/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/pausar categoria/i)).not.toBeInTheDocument();
  });

  it('testOperationalStatusShown: columna de estado operativo presente', async () => {
    render(
      <MemoryRouter>
        <ChefDishesPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/estado operativo/i)).toBeInTheDocument();
    });
  });
});
