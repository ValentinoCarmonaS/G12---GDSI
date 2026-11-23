import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../../src/context/AuthContext';
import { StaffManagementPage } from '../../src/pages/StaffManagementPage';
import { InventoryManagementPage } from '../../src/pages/InventoryManagementPage';
import * as staffApi from '../../src/api/staffApi';
import * as inventoryApi from '../../src/api/inventoryApi';

vi.mock('../../src/api/staffApi');
vi.mock('../../src/api/inventoryApi');

const mockStaffList = [
  {
    id: 'staff-1',
    fullName: 'María Gómez',
    email: 'maria@easyeat.test',
    role: 'ADMINISTRADOR' as const,
    status: 'ACTIVE' as const,
    createdAt: '2026-10-01T10:00:00Z',
  },
  {
    id: 'staff-2',
    fullName: 'Carlos Pérez',
    email: 'carlos@easyeat.test',
    role: 'CHEF' as const,
    status: 'PENDING_FIRST_LOGIN' as const,
    createdAt: '2026-10-02T10:00:00Z',
  },
  {
    id: 'staff-3',
    fullName: 'Lucía Díaz',
    email: 'lucia@easyeat.test',
    role: 'ENCARGADO' as const,
    status: 'SUSPENDED' as const,
    createdAt: '2026-10-03T10:00:00Z',
  },
];

const mockIngredientsList = [
  {
    id: 'ing-1',
    name: 'Harina 000',
    category: 'Dry Goods' as const,
    unitOfMeasure: 'kg' as const,
    currentStock: 2,
    criticalStockThreshold: 5,
    unitPurchaseCost: 1200,
    status: 'ACTIVE' as const,
    isCritical: true,
    updatedAt: '2026-10-05T10:00:00Z',
  },
  {
    id: 'ing-2',
    name: 'Aceite de Oliva',
    category: 'Dry Goods' as const,
    unitOfMeasure: 'l' as const,
    currentStock: 12,
    criticalStockThreshold: 4,
    unitPurchaseCost: 4500,
    status: 'ACTIVE' as const,
    isCritical: false,
    updatedAt: '2026-10-05T10:00:00Z',
  },
];

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <AuthProvider>
      <MemoryRouter>{ui}</MemoryRouter>
    </AuthProvider>
  );

describe('Spec 026 US4 — Staff & Inventory Glassmorphism', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(staffApi.staffApi.listStaff).mockResolvedValue(mockStaffList as any);
    vi.mocked(inventoryApi.inventoryApi.listIngredients).mockResolvedValue(mockIngredientsList as any);
    vi.mocked(inventoryApi.inventoryApi.getBatches).mockResolvedValue([]);
  });

  describe('StaffManagementPage & StaffRosterTable (GlassCard + AccessibleModal)', () => {
    it('renders staff roster as GlassCard cards with StatusBadge and >=44px touch targets', async () => {
      const { container } = renderWithProviders(<StaffManagementPage />);

      await waitFor(() => {
        expect(screen.getByText('María Gómez')).toBeInTheDocument();
      });

      expect(screen.getByText('Activo')).toBeInTheDocument();
      expect(screen.getByText('Pendiente de primer login')).toBeInTheDocument();
      expect(screen.getByText('Suspendido')).toBeInTheDocument();

      const glassCards = container.querySelectorAll('[class*="backdrop-blur-[12px]"]');
      expect(glassCards.length).toBeGreaterThanOrEqual(3);

      const actionButtons = screen.getAllByRole('button', { name: /Editar|Suspender|Reactivar/i });
      expect(actionButtons.length).toBeGreaterThan(0);
      actionButtons.forEach((btn) => {
        expect(btn.className).toMatch(/min-h-\[44px\]/);
      });
    });

    it('opens StaffFormModal and TemporaryCredentialsModal via AccessibleModal and closes on Escape', async () => {
      vi.mocked(staffApi.staffApi.createStaff).mockResolvedValue({
        id: 'staff-4',
        fullName: 'Nuevo Chef',
        email: 'nuevo@easyeat.test',
        role: 'CHEF' as const,
        status: 'PENDING_FIRST_LOGIN' as const,
        temporaryCredential: 'Temp#1234',
      } as any);

      renderWithProviders(<StaffManagementPage />);

      await waitFor(() => {
        expect(screen.getByText('María Gómez')).toBeInTheDocument();
      });

      const addBtn = screen.getByRole('button', { name: /Nuevo Empleado/i });
      fireEvent.click(addBtn);

      const dialog = await screen.findByRole('dialog');
      expect(dialog).toBeInTheDocument();

      fireEvent.keyDown(document, { key: 'Escape' });
      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
    });

    it('opens StaffStatusModal via AccessibleModal and surfaces único admin error', async () => {
      vi.mocked(staffApi.staffApi.updateStaffStatus).mockRejectedValue({
        response: { status: 409, data: { detail: 'No se puede suspender al único administrador activo.' } },
      });

      renderWithProviders(<StaffManagementPage />);

      await waitFor(() => {
        expect(screen.getByText('María Gómez')).toBeInTheDocument();
      });

      const suspendButtons = screen.getAllByRole('button', { name: /Suspender/i });
      fireEvent.click(suspendButtons[0]);

      const dialog = await screen.findByRole('dialog');
      expect(dialog).toBeInTheDocument();

      const confirmBtn = screen.getByRole('button', { name: /Confirmar suspensión/i });
      fireEvent.click(confirmBtn);

      await waitFor(() => {
        expect(screen.getByText(/único administrador/i)).toBeInTheDocument();
      });
    });
  });

  describe('InventoryManagementPage & IngredientCatalogTable (GlassCard + AccessibleModal)', () => {
    it('renders ingredients as GlassCard cards with JetBrains Mono numbers and critical stock redundant signaling', async () => {
      const { container } = renderWithProviders(<InventoryManagementPage />);

      await waitFor(() => {
        expect(screen.getByText('Harina 000')).toBeInTheDocument();
      });

      const glassCards = container.querySelectorAll('[class*="backdrop-blur-[12px]"]');
      expect(glassCards.length).toBeGreaterThanOrEqual(2);

      const monoElements = container.querySelectorAll('.font-ee-mono');
      expect(monoElements.length).toBeGreaterThan(0);

      expect(screen.getByLabelText(/Solo insumos en stock crítico/i)).toBeInTheDocument();
      expect(screen.getAllByText(/Stock Crítico/i).length).toBeGreaterThanOrEqual(1);
    });

    it('opens IngredientFormModal, StockAdjustmentModal and StockAuditModal via AccessibleModal', async () => {
      vi.mocked(inventoryApi.inventoryApi.getAuditHistory).mockResolvedValue([
        {
          id: 'audit-1',
          previousStock: 5,
          newStock: 2,
          netDelta: -3,
          justification: 'Consumo cocina',
          createdAt: '2026-10-05T10:00:00Z',
          adminName: 'María Gómez',
        },
      ] as any);

      renderWithProviders(<InventoryManagementPage />);

      await waitFor(() => {
        expect(screen.getByText('Harina 000')).toBeInTheDocument();
      });

      const addBtn = screen.getByRole('button', { name: /Nuevo Ingrediente/i });
      fireEvent.click(addBtn);
      expect(await screen.findByRole('dialog')).toBeInTheDocument();
      fireEvent.keyDown(document, { key: 'Escape' });
      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });

      const adjustButtons = screen.getAllByRole('button', { name: /Ajustar Stock/i });
      fireEvent.click(adjustButtons[0]);
      expect(await screen.findByRole('dialog')).toBeInTheDocument();
      fireEvent.keyDown(document, { key: 'Escape' });
      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });

      const auditButtons = screen.getAllByRole('button', { name: /Historial|Auditoría/i });
      fireEvent.click(auditButtons[0]);
      expect(await screen.findByRole('dialog')).toBeInTheDocument();
      await waitFor(() => {
        expect(screen.getByText('Consumo cocina')).toBeInTheDocument();
      });
    });
  });
});
