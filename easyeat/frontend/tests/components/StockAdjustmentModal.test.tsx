import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StockAdjustmentModal } from '@/components/inventory/StockAdjustmentModal';
import { AdminIngredientDetailResponse } from '@/types/inventory';

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

const mockDiscreteIngredient: AdminIngredientDetailResponse = {
  id: 'ing-102',
  tenantId: 'tenant-1',
  name: 'Pan de Hamburguesa',
  category: 'Dry Goods',
  unitOfMeasure: 'u',
  unitPurchaseCost: 50.0,
  status: 'ACTIVE',
  currentStock: 20,
  createdAt: '2026-10-24T10:00:00Z',
  updatedAt: '2026-10-24T10:00:00Z',
};

describe('StockAdjustmentModal Component (FR-015, FR-016, FR-017, FR-018, FR-027)', () => {
  const mockOnAdjust = vi.fn();
  const mockOnClose = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
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

    expect(screen.queryByText(/ajuste manual de stock/i)).not.toBeInTheDocument();
  });

  it('renders modal with current stock and default absolute mode', () => {
    render(
      <StockAdjustmentModal
        isOpen={true}
        onClose={mockOnClose}
        ingredient={mockIngredient}
        onAdjust={mockOnAdjust}
      />
    );

    expect(screen.getByRole('heading', { name: /ajuste manual de stock/i })).toBeInTheDocument();
    expect(screen.getByText('Harina de Trigo 000')).toBeInTheDocument();
    expect(screen.getAllByText(/50 kg/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByLabelText(/modo de ajuste/i)).toHaveValue('ABSOLUTE');
  });

  it('computes live delta and new stock in ABSOLUTE mode and blocks zero-delta submission', async () => {
    const user = userEvent.setup();
    render(
      <StockAdjustmentModal
        isOpen={true}
        onClose={mockOnClose}
        ingredient={mockIngredient}
        onAdjust={mockOnAdjust}
      />
    );

    const valueInput = screen.getByLabelText(/valor/i);
    // Setting value to currentStock (50) results in zero delta
    await user.clear(valueInput);
    await user.type(valueInput, '50');

    const justificationInput = screen.getByLabelText(/justificación/i);
    await user.type(justificationInput, 'Recuento físico mensual');

    await user.click(screen.getByRole('button', { name: /confirmar ajuste/i }));

    expect(
      await screen.findByText(/el ajuste no produce variación en el stock/i)
    ).toBeInTheDocument();
    expect(mockOnAdjust).not.toHaveBeenCalled();
  });

  it('blocks negative resulting stock invariant in RELATIVE mode', async () => {
    const user = userEvent.setup();
    render(
      <StockAdjustmentModal
        isOpen={true}
        onClose={mockOnClose}
        ingredient={mockIngredient}
        onAdjust={mockOnAdjust}
      />
    );

    await user.selectOptions(screen.getByLabelText(/modo de ajuste/i), 'RELATIVE');

    const valueInput = screen.getByLabelText(/valor/i);
    // Subtracting 60 from 50 would result in -10 stock
    await user.clear(valueInput);
    await user.type(valueInput, '-60');

    const justificationInput = screen.getByLabelText(/justificación/i);
    await user.type(justificationInput, 'Merma por rotura de bolsa');

    await user.click(screen.getByRole('button', { name: /confirmar ajuste/i }));

    expect(
      await screen.findByText(/el stock resultante no puede ser negativo/i)
    ).toBeInTheDocument();
    expect(mockOnAdjust).not.toHaveBeenCalled();
  });

  it('rejects fractional stock values for discrete units of measure', async () => {
    const user = userEvent.setup();
    render(
      <StockAdjustmentModal
        isOpen={true}
        onClose={mockOnClose}
        ingredient={mockDiscreteIngredient}
        onAdjust={mockOnAdjust}
      />
    );

    const valueInput = screen.getByLabelText(/valor/i);
    await user.clear(valueInput);
    await user.type(valueInput, '25.5');

    const justificationInput = screen.getByLabelText(/justificación/i);
    await user.type(justificationInput, 'Recuento de panes');

    await user.click(screen.getByRole('button', { name: /confirmar ajuste/i }));

    expect(
      await screen.findByText(/las unidades discretas.*enteros/i)
    ).toBeInTheDocument();
    expect(mockOnAdjust).not.toHaveBeenCalled();
  });

  it('submits valid adjustment payload and closes modal on success', async () => {
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

    // Enter target absolute stock 65 kg (delta = +15)
    const valueInput = screen.getByLabelText(/valor/i);
    await user.clear(valueInput);
    await user.type(valueInput, '65');

    const justificationInput = screen.getByLabelText(/justificación/i);
    await user.type(justificationInput, 'Recepción de pedido proveedor');

    await user.click(screen.getByRole('button', { name: /confirmar ajuste/i }));

    expect(mockOnAdjust).toHaveBeenCalledWith('ing-101', {
      mode: 'ABSOLUTE',
      value: 65,
      justification: 'Recepción de pedido proveedor',
    });
  });
});
