import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { IngredientCatalogTable } from '@/components/inventory/IngredientCatalogTable';
import type { AdminIngredientDetailResponse } from '@/types/inventory';

const mockIngredients: AdminIngredientDetailResponse[] = [
  {
    id: 'ing-1',
    tenantId: 'tenant-1',
    name: 'Harina 000',
    category: 'Dry Goods',
    unitOfMeasure: 'kg',
    unitPurchaseCost: 150.0,
    status: 'ACTIVE',
    currentStock: 40.0,
    createdAt: '2026-10-24T10:00:00Z',
    updatedAt: '2026-10-24T10:00:00Z',
  },
  {
    id: 'ing-2',
    tenantId: 'tenant-1',
    name: 'Levadura Fresca',
    category: 'Dry Goods',
    unitOfMeasure: 'g',
    unitPurchaseCost: 20.0,
    status: 'DISCONTINUED',
    currentStock: 100.0,
    createdAt: '2026-10-24T11:00:00Z',
    updatedAt: '2026-10-24T11:00:00Z',
  },
];

describe('IngredientCatalogTable Component (FR-014, FR-031)', () => {
  const mockOnEdit = vi.fn();
  const mockOnStatusTransition = vi.fn();
  const mockOnAdjustStock = vi.fn();
  const mockOnViewAudit = vi.fn();

  it('renders ingredient rows with active/discontinued status badges', () => {
    render(
      <IngredientCatalogTable
        ingredients={mockIngredients}
        onEdit={mockOnEdit}
        onStatusTransition={mockOnStatusTransition}
        onAdjustStock={mockOnAdjustStock}
        onViewAudit={mockOnViewAudit}
      />
    );

    expect(screen.getByText('Harina 000')).toBeInTheDocument();
    expect(screen.getByText('Levadura Fresca')).toBeInTheDocument();
    expect(screen.getAllByText('Activo').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Desactivado').length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText(/costo referencia/i)).not.toBeInTheDocument();
  });

  it('renders Desactivar for ACTIVE ingredient and Reactivar for DISCONTINUED ingredient without Archivar', async () => {
    const user = userEvent.setup();

    render(
      <IngredientCatalogTable
        ingredients={mockIngredients}
        onEdit={mockOnEdit}
        onStatusTransition={mockOnStatusTransition}
        onAdjustStock={mockOnAdjustStock}
        onViewAudit={mockOnViewAudit}
      />
    );

    // Verify Archivar does NOT exist
    expect(screen.queryByRole('button', { name: /archivar/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /desarchivar/i })).not.toBeInTheDocument();

    // Verify Desactivar is available for Active item
    const deactivateBtn = screen.getByRole('button', { name: /desactivar/i });
    expect(deactivateBtn).toBeInTheDocument();
    await user.click(deactivateBtn);
    expect(mockOnStatusTransition).toHaveBeenCalledWith('ing-1', 'DISCONTINUED');

    // Verify Reactivar is available for Discontinued item
    const reactivateBtn = screen.getByRole('button', { name: /reactivar/i });
    expect(reactivateBtn).toBeInTheDocument();
    await user.click(reactivateBtn);
    expect(mockOnStatusTransition).toHaveBeenCalledWith('ing-2', 'ACTIVE');
  });

  describe('Critical Stock Alerts & Quick Filter (US2: INV-4, INV-17, FR-005, FR-006, FR-020, EC-020, US2 AC-2.5, AC-2.6)', () => {
    const criticalIngredients: AdminIngredientDetailResponse[] = [
      {
        id: 'crit-1',
        tenantId: 'tenant-1',
        name: 'Tomate Perita',
        category: 'Produce',
        unitOfMeasure: 'kg',
        unitPurchaseCost: 50.0,
        status: 'ACTIVE',
        currentStock: 2.0,
        criticalStockThreshold: 10,
        isCritical: true,
        createdAt: '2026-10-24T10:00:00Z',
        updatedAt: '2026-10-24T10:00:00Z',
      },
      {
        id: 'normal-1',
        tenantId: 'tenant-1',
        name: 'Cebolla',
        category: 'Produce',
        unitOfMeasure: 'kg',
        unitPurchaseCost: 30.0,
        status: 'ACTIVE',
        currentStock: 15.0,
        criticalStockThreshold: 5,
        isCritical: false,
        createdAt: '2026-10-24T10:00:00Z',
        updatedAt: '2026-10-24T10:00:00Z',
      },
      {
        id: 'crit-2',
        tenantId: 'tenant-1',
        name: 'Queso Muzzarella',
        category: 'Dairy',
        unitOfMeasure: 'kg',
        unitPurchaseCost: 120.0,
        status: 'ACTIVE',
        currentStock: 1.0,
        criticalStockThreshold: 8,
        isCritical: true,
        createdAt: '2026-10-24T10:00:00Z',
        updatedAt: '2026-10-24T10:00:00Z',
      },
      {
        id: 'disc-crit',
        tenantId: 'tenant-1',
        name: 'Aceite de Oliva Descontinuado',
        category: 'Dry Goods',
        unitOfMeasure: 'l',
        unitPurchaseCost: 200.0,
        status: 'DISCONTINUED',
        currentStock: 1.0,
        criticalStockThreshold: 5,
        isCritical: false, // Insumos descontinuados nunca tienen alerta visual activa (INV-17, FR-021)
        createdAt: '2026-10-24T10:00:00Z',
        updatedAt: '2026-10-24T10:00:00Z',
      },
    ];

    it('testHighlightCriticalIngredients - displays "⚠️ Stock crítico" badge when isCritical is true (INV-4, FR-005)', () => {
      render(
        <IngredientCatalogTable
          ingredients={[criticalIngredients[0]]}
          onEdit={mockOnEdit}
        />
      );

      expect(screen.getByText('Tomate Perita')).toBeInTheDocument();
      expect(screen.getByText(/⚠️\s*Stock crítico/i)).toBeInTheDocument();
    });

    it('testMultipleAlertsRendered - renders multiple critical alert badges simultaneously without degradation (EC-020, US2 AC-2.5)', () => {
      render(
        <IngredientCatalogTable
          ingredients={criticalIngredients}
          onEdit={mockOnEdit}
        />
      );

      const badges = screen.getAllByText(/⚠️\s*Stock crítico/i);
      expect(badges).toHaveLength(2);
    });

    it('testNoAlertWhenAboveThreshold - does not render critical alert badge when currentStock >= threshold (FR-006)', () => {
      render(
        <IngredientCatalogTable
          ingredients={[criticalIngredients[1]]}
          onEdit={mockOnEdit}
        />
      );

      expect(screen.getByText('Cebolla')).toBeInTheDocument();
      expect(screen.queryByText(/⚠️\s*Stock crítico/i)).not.toBeInTheDocument();
    });

    it('testDiscontinuedNoAlert - does not display critical alert badge for DISCONTINUED ingredients even if stock < threshold (INV-17, FR-021)', () => {
      render(
        <IngredientCatalogTable
          ingredients={[criticalIngredients[3]]}
          onEdit={mockOnEdit}
        />
      );

      expect(screen.getByText('Aceite de Oliva Descontinuado')).toBeInTheDocument();
      expect(screen.queryByText(/⚠️\s*Stock crítico/i)).not.toBeInTheDocument();
    });

    it('testCriticalOnlyFilterToggles - toggling quick filter filters list to only show critical ingredients (FR-020, US2 AC-2.6)', async () => {
      const user = userEvent.setup();
      const mockOnToggleCriticalOnly = vi.fn();

      render(
        <IngredientCatalogTable
          ingredients={criticalIngredients}
          onEdit={mockOnEdit}
          onToggleCriticalOnly={mockOnToggleCriticalOnly}
        />
      );

      // Search for the quick filter toggle or checkbox
      const criticalFilterControl = screen.getByRole('checkbox', {
        name: /solo insumos en stock crítico|stock crítico/i,
      });
      expect(criticalFilterControl).toBeInTheDocument();

      await user.click(criticalFilterControl);
      expect(mockOnToggleCriticalOnly).toHaveBeenCalledWith(true);
    });
  });
});
