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
});
