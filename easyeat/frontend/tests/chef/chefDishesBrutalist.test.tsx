import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ChefDishesPage } from '../../src/pages/ChefDishesPage';
import { dishApi } from '@/api/dishApi';

vi.mock('@/api/dishApi', () => ({
  dishApi: {
    listDishes: vi.fn(),
    toggleChefPause: vi.fn(),
    deleteDish: vi.fn(),
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

vi.mock('@/hooks/useChefDishesSync', () => ({
  useChefDishesSync: () => ({
    items: [],
    isLoading: false,
    error: null,
  }),
}));

const mockDishes = [
  {
    id: 'dish-draft',
    name: 'Empanada Salteña',
    category: 'Entradas',
    status: 'DRAFT',
    isActive: false,
    pausedByChef: false,
    photoCount: 1,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
  },
  {
    id: 'dish-pending',
    name: 'Locro Criollo',
    category: 'Platos principales',
    status: 'PENDING_PRICE',
    isActive: false,
    pausedByChef: false,
    photoCount: 1,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
  },
  {
    id: 'dish-pub-active',
    name: 'Milanesa Napolitana',
    category: 'Platos principales',
    status: 'PUBLISHED',
    isActive: true,
    pausedByChef: false,
    photoCount: 1,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
  },
  {
    id: 'dish-pub-paused',
    name: 'Flan con Dulce',
    category: 'Postres',
    status: 'PUBLISHED',
    isActive: true,
    pausedByChef: true,
    photoCount: 1,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
  },
  {
    id: 'dish-cancelled',
    name: 'Sopa Vieja',
    category: 'Entradas',
    status: 'CANCELLED',
    isActive: false,
    pausedByChef: false,
    photoCount: 0,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
  },
];

describe('ChefDishesPage Brutalism (US3)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(dishApi.listDishes).mockResolvedValue({
      content: mockDishes,
      totalPages: 1,
      totalElements: 5,
      size: 20,
      number: 0,
    } as any);
  });

  it('1. Renderiza cabecera con Nuevo Plato (>=48px), rótulo único Estado Operativo y barra de chips táctiles para 5 estados', async () => {
    render(
      <MemoryRouter>
        <ChefDishesPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Milanesa Napolitana')).toBeInTheDocument();
    });

    const newDishLink = screen.getByRole('link', { name: /nuevo plato/i });
    expect(newDishLink.className).toMatch(/min-h-\[48px\]/);

    // Rótulo único Estado Operativo a nivel de vista
    expect(screen.getAllByText(/estado operativo/i)).toHaveLength(1);

    // No debe existir <select> para filtrar por estado
    expect(document.querySelector('select')).toBeNull();

    const chipLabels = [
      /todos los estados/i,
      /borrador/i,
      /pendiente de aprobación/i,
      /publicado/i,
      /descartado/i,
    ];

    for (const label of chipLabels) {
      const chip = screen.getByRole('button', { name: label });
      expect(chip).toHaveAttribute('aria-pressed');
      expect(chip.className).toMatch(/min-h-\[48px\]/);
    }

    const draftChip = screen.getByRole('button', { name: /borrador/i });
    fireEvent.click(draftChip);

    await waitFor(() => {
      expect(dishApi.listDishes).toHaveBeenCalledWith({
        status: 'DRAFT',
        page: 0,
        size: 20,
      });
    });
  });

  it('2. Renderiza grilla responsiva de tarjetas Brutalism sin <table> ni scroll horizontal y acciones táctiles (>=48px)', async () => {
    render(
      <MemoryRouter>
        <ChefDishesPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Empanada Salteña')).toBeInTheDocument();
    });

    expect(document.querySelector('table')).toBeNull();

    const editLinks = screen.getAllByRole('link', { name: /editar/i });
    expect(editLinks.length).toBeGreaterThanOrEqual(4);
    for (const link of editLinks) {
      expect(link.className).toMatch(/min-h-\[48px\]/);
    }

    const deleteBtn = screen.getByRole('button', { name: /eliminar/i });
    expect(deleteBtn.className).toMatch(/min-h-\[48px\]/);
  });

  it('3. Renderiza ChefDishPauseToggle (>=56px) solo en PUBLISHED con actualización optimista/reversión y cero pausa masiva', async () => {
    vi.mocked(dishApi.toggleChefPause).mockRejectedValueOnce(new Error('Network error'));

    render(
      <MemoryRouter>
        <ChefDishesPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Milanesa Napolitana')).toBeInTheDocument();
    });

    const switches = screen.getAllByRole('switch');
    expect(switches).toHaveLength(2);
    for (const sw of switches) {
      expect(sw.className).toMatch(/min-h-\[56px\]/);
    }

    expect(screen.getByText('Activo para comensales')).toBeInTheDocument();
    expect(screen.getByText('Pausado por cocina')).toBeInTheDocument();
    expect(screen.queryByText(/pausar todos/i)).not.toBeInTheDocument();

    // Click en el primer switch (Activo -> Pausado -> falla -> revierte a Activo)
    fireEvent.click(switches[0]);

    await waitFor(() => {
      expect(dishApi.toggleChefPause).toHaveBeenCalledWith('dish-pub-active', true);
    });

    await waitFor(() => {
      expect(switches[0]).toHaveAttribute('aria-checked', 'false');
    });
  });

  it('4. Elimina borrador DRAFT vía BrutalistModal (>=56px, Escape) sin window.confirm/alert y muestra error inline en FeedbackState', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockImplementation(() => true);
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    vi.mocked(dishApi.deleteDish).mockRejectedValueOnce(new Error('No se pudo eliminar'));

    render(
      <MemoryRouter>
        <ChefDishesPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Empanada Salteña')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /eliminar/i }));

    expect(confirmSpy).not.toHaveBeenCalled();

    const modal = await screen.findByTestId('brutalist-modal');
    expect(modal).toBeInTheDocument();

    const confirmDeleteBtn = screen.getByRole('button', { name: /confirmar eliminación/i });
    const cancelBtn = screen.getByRole('button', { name: /cancelar/i });
    expect(confirmDeleteBtn.className).toMatch(/min-h-\[56px\]/);
    expect(cancelBtn.className).toMatch(/min-h-\[56px\]/);

    // Escape cierra el modal
    fireEvent.keyDown(document, { key: 'Escape' });
    await waitFor(() => {
      expect(screen.queryByTestId('brutalist-modal')).not.toBeInTheDocument();
    });

    // Reabrir y confirmar eliminación con error
    fireEvent.click(screen.getByRole('button', { name: /eliminar/i }));
    fireEvent.click(await screen.findByRole('button', { name: /confirmar eliminación/i }));

    await waitFor(() => {
      expect(dishApi.deleteDish).toHaveBeenCalledWith('dish-draft');
    });

    expect(alertSpy).not.toHaveBeenCalled();
    expect(await screen.findByRole('alert')).toHaveTextContent(/error al eliminar el borrador/i);

    confirmSpy.mockRestore();
    alertSpy.mockRestore();
  });

  it('5. Muestra LoadingSkeleton, EmptyState y paginación Brutalism', async () => {
    let resolveList: (val: any) => void;
    vi.mocked(dishApi.listDishes).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveList = resolve;
        })
    );

    render(
      <MemoryRouter>
        <ChefDishesPage />
      </MemoryRouter>
    );

    expect(screen.getByTestId('chef-dishes-loading')).toBeInTheDocument();

    resolveList!({
      content: [],
      totalPages: 0,
      totalElements: 0,
      size: 20,
      number: 0,
    });

    await waitFor(() => {
      expect(screen.getByText(/no hay platos registrados/i)).toBeInTheDocument();
    });
  });
});
