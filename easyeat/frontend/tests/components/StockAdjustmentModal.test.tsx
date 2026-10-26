import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StockAdjustmentModal } from '@/components/inventory/StockAdjustmentModal';
import { inventoryApi } from '@/api/inventoryApi';
import type { AdminIngredientDetailResponse, IngredientBatch } from '@/types/inventory';

vi.mock('@/api/inventoryApi', () => ({
  inventoryApi: {
    getBatches: vi.fn(),
  },
}));

const mockIngredient: AdminIngredientDetailResponse = {
  id: 'ing-101',
  tenantId: 'tenant-1',
  name: 'Harina de Trigo 000',
  category: 'Dry Goods',
  unitOfMeasure: 'kg',
  unitPurchaseCost: 100.0,
  status: 'ACTIVE',
  currentStock: 50.0,
  createdAt: '2026-10-24T10:00:00Z',
  updatedAt: '2026-10-24T10:00:00Z',
};

const mockDiscontinuedIngredient: AdminIngredientDetailResponse = {
  ...mockIngredient,
  id: 'ing-103',
  status: 'DISCONTINUED',
};

const mockBatches: IngredientBatch[] = [
  {
    id: 'batch-1',
    ingredientId: 'ing-101',
    initialQuantity: 30,
    remainingQuantity: 20,
    unitCost: 120.0,
    createdAt: '2026-10-20T10:00:00Z',
  },
  {
    id: 'batch-2',
    ingredientId: 'ing-101',
    initialQuantity: 40,
    remainingQuantity: 30,
    unitCost: 150.0,
    createdAt: '2026-10-22T10:00:00Z',
  },
];

describe('StockAdjustmentModal Component (FR-015, FR-015a, FR-015b, FR-027)', () => {
  const mockOnAdjust = vi.fn();
  const mockOnClose = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(inventoryApi.getBatches).mockResolvedValue(mockBatches);
  });

  it('does not render modal when isOpen is false', () => {
    render(
      <StockAdjustmentModal
        isOpen={false}
        onClose={mockOnClose}
        ingredient={mockIngredient}
        onAdjust={mockOnAdjust}
      />
    );

    expect(screen.queryByText(/ajuste de stock/i)).not.toBeInTheDocument();
  });

  it('renders modal with tabs Agregar stock and Restar stock', () => {
    render(
      <StockAdjustmentModal
        isOpen={true}
        onClose={mockOnClose}
        ingredient={mockIngredient}
        onAdjust={mockOnAdjust}
      />
    );

    expect(screen.getByRole('tab', { name: /agregar stock/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /restar stock/i })).toBeInTheDocument();
    expect(screen.getByText('Harina de Trigo 000')).toBeInTheDocument();
  });

  it('submits valid ADD stock payload with unit purchase cost', async () => {
    const user = userEvent.setup();
    mockOnAdjust.mockResolvedValueOnce(undefined);

    render(
      <StockAdjustmentModal
        isOpen={true}
        onClose={mockOnClose}
        ingredient={mockIngredient}
        onAdjust={mockOnAdjust}
      />
    );

    const qtyInput = screen.getByLabelText(/cantidad a ingresar/i);
    await user.type(qtyInput, '25');

    const costInput = screen.getByLabelText(/precio unitario de compra/i);
    await user.clear(costInput);
    await user.type(costInput, '180');

    const justificationInput = screen.getByLabelText(/justificación/i);
    await user.type(justificationInput, 'Recepción factura #9921');

    await user.click(screen.getByRole('button', { name: /confirmar ingreso/i }));

    expect(mockOnAdjust).toHaveBeenCalledWith('ing-101', {
      action: 'ADD',
      quantity: 25,
      unitCost: 180,
      justification: 'Recepción factura #9921',
    });
  });

  it('validates mandatory purchase cost on ADD stock', async () => {
    const user = userEvent.setup();

    render(
      <StockAdjustmentModal
        isOpen={true}
        onClose={mockOnClose}
        ingredient={mockIngredient}
        onAdjust={mockOnAdjust}
      />
    );

    const qtyInput = screen.getByLabelText(/cantidad a ingresar/i);
    await user.type(qtyInput, '10');

    const costInput = screen.getByLabelText(/precio unitario de compra/i);
    await user.clear(costInput);

    const justificationInput = screen.getByLabelText(/justificación/i);
    await user.type(justificationInput, 'Ingreso sin costo');

    await user.click(screen.getByRole('button', { name: /confirmar ingreso/i }));

    expect(await screen.findByText(/el costo unitario de compra es obligatorio/i)).toBeInTheDocument();
    expect(mockOnAdjust).not.toHaveBeenCalled();
  });

  it('switches to Restar stock tab, selects batch, displays live financial loss, and submits SUBTRACT payload', async () => {
    const user = userEvent.setup();
    mockOnAdjust.mockResolvedValueOnce(undefined);

    render(
      <StockAdjustmentModal
        isOpen={true}
        onClose={mockOnClose}
        ingredient={mockIngredient}
        onAdjust={mockOnAdjust}
      />
    );

    // Click Restar stock tab
    await user.click(screen.getByRole('tab', { name: /restar stock/i }));

    await waitFor(() => {
      expect(inventoryApi.getBatches).toHaveBeenCalledWith('ing-101');
    });

    // Select batch 1 ($120/kg, 20 kg remaining)
    const batchSelect = await screen.findByLabelText(/lote a descontar/i);
    await user.selectOptions(batchSelect, 'batch-1');

    const qtyInput = screen.getByLabelText(/cantidad a descontar/i);
    await user.type(qtyInput, '5');

    // Live financial loss should display: 5 * 120 = $600
    expect(screen.getByText(/\$600/)).toBeInTheDocument();

    const justificationInput = screen.getByLabelText(/justificación/i);
    await user.type(justificationInput, 'Merma por humedad');

    await user.click(screen.getByRole('button', { name: /confirmar merma/i }));

    expect(mockOnAdjust).toHaveBeenCalledWith('ing-101', {
      action: 'SUBTRACT',
      quantity: 5,
      batchId: 'batch-1',
      justification: 'Merma por humedad',
    });
  });

  it('rejects deduction quantity exceeding available batch balance', async () => {
    const user = userEvent.setup();

    render(
      <StockAdjustmentModal
        isOpen={true}
        onClose={mockOnClose}
        ingredient={mockIngredient}
        onAdjust={mockOnAdjust}
      />
    );

    await user.click(screen.getByRole('tab', { name: /restar stock/i }));

    const batchSelect = await screen.findByLabelText(/lote a descontar/i);
    await user.selectOptions(batchSelect, 'batch-1'); // 20 kg available

    const qtyInput = screen.getByLabelText(/cantidad a descontar/i);
    await user.type(qtyInput, '25'); // Exceeds 20 kg

    const justificationInput = screen.getByLabelText(/justificación/i);
    await user.type(justificationInput, 'Intento de merma excesiva');

    await user.click(screen.getByRole('button', { name: /confirmar merma/i }));

    expect(await screen.findByText(/la cantidad a descontar supera el saldo disponible del lote/i)).toBeInTheDocument();
    expect(mockOnAdjust).not.toHaveBeenCalled();
  });

  it('disables Agregar stock tab when ingredient is DISCONTINUED', () => {
    render(
      <StockAdjustmentModal
        isOpen={true}
        onClose={mockOnClose}
        ingredient={mockDiscontinuedIngredient}
        onAdjust={mockOnAdjust}
      />
    );

    const addTab = screen.getByRole('tab', { name: /agregar stock/i });
    expect(addTab).toBeDisabled();
    expect(screen.getByRole('tab', { name: /restar stock/i })).toHaveAttribute('aria-selected', 'true');
  });
});
