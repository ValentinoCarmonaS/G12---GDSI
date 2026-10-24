import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import { StockAuditModal } from '@/components/inventory/StockAuditModal';
import { inventoryApi } from '@/api/inventoryApi';
import { AdminIngredientDetailResponse, StockAuditEntryResponse } from '@/types/inventory';

vi.mock('@/api/inventoryApi');

const mockIngredient: AdminIngredientDetailResponse = {
  id: 'ing-201',
  tenantId: 'tenant-1',
  name: 'Carne Picada Especial',
  category: 'Meat',
  unitOfMeasure: 'kg',
  unitPurchaseCost: 1200.0,
  status: 'ACTIVE',
  currentStock: 35.0,
  createdAt: '2026-10-24T10:00:00Z',
  updatedAt: '2026-10-24T10:00:00Z',
};

const mockAuditEntries: StockAuditEntryResponse[] = [
  {
    id: 'audit-1',
    ingredientId: 'ing-201',
    userId: 'user-admin-1',
    adminName: 'Administrador Principal',
    adjustmentMode: 'ABSOLUTE',
    previousStock: 0,
    newStock: 50.0,
    netDelta: 50.0,
    justification: 'Stock inicial de apertura',
    createdAt: '2026-10-24T10:00:00Z',
  },
  {
    id: 'audit-2',
    ingredientId: 'ing-201',
    userId: 'user-admin-1',
    adminName: 'Administrador Principal',
    adjustmentMode: 'RELATIVE',
    previousStock: 50.0,
    newStock: 35.0,
    netDelta: -15.0,
    justification: 'Merma por descarte de grasa y hueso',
    createdAt: '2026-10-24T12:00:00Z',
  },
];

describe('StockAuditModal Component (FR-019)', () => {
  const mockOnClose = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('does not render modal when isOpen is false', () => {
    render(
      <StockAuditModal
        isOpen={false}
        onClose={mockOnClose}
        ingredient={mockIngredient}
      />
    );

    expect(screen.queryByText(/pista de auditoría/i)).not.toBeInTheDocument();
  });

  it('loads and displays chronological audit trail entries for the ingredient', async () => {
    vi.mocked(inventoryApi.getAuditHistory).mockResolvedValueOnce(mockAuditEntries);

    render(
      <StockAuditModal
        isOpen={true}
        onClose={mockOnClose}
        ingredient={mockIngredient}
      />
    );

    expect(screen.getByRole('heading', { name: /pista de auditoría/i })).toBeInTheDocument();
    expect(screen.getByText('Carne Picada Especial')).toBeInTheDocument();

    await waitFor(() => {
      expect(inventoryApi.getAuditHistory).toHaveBeenCalledWith('ing-201');
    });

    expect(await screen.findByText('Stock inicial de apertura')).toBeInTheDocument();
    expect(screen.getByText('Merma por descarte de grasa y hueso')).toBeInTheDocument();
    expect(screen.getByText('+50 kg')).toBeInTheDocument();
    expect(screen.getByText('-15 kg')).toBeInTheDocument();
  });

  it('displays empty state message when no audit entries exist', async () => {
    vi.mocked(inventoryApi.getAuditHistory).mockResolvedValueOnce([]);

    render(
      <StockAuditModal
        isOpen={true}
        onClose={mockOnClose}
        ingredient={mockIngredient}
      />
    );

    expect(
      await screen.findByText(/no se registran movimientos de stock para este ingrediente/i)
    ).toBeInTheDocument();
  });

  it('closes modal on close button click', async () => {
    const user = userEvent.setup();
    vi.mocked(inventoryApi.getAuditHistory).mockResolvedValueOnce(mockAuditEntries);

    render(
      <StockAuditModal
        isOpen={true}
        onClose={mockOnClose}
        ingredient={mockIngredient}
      />
    );

    const closeButton = screen.getByRole('button', { name: /cerrar/i });
    await user.click(closeButton);

    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });
});
