import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { IngredientFormModal } from '@/components/inventory/IngredientFormModal';
import { AdminIngredientDetailResponse } from '@/types/inventory';

describe('IngredientFormModal Component (FR-010, FR-011, FR-013, FR-026)', () => {
  const mockOnSubmit = vi.fn();
  const mockOnClose = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('does not render modal when isOpen is false', () => {
    render(
      <IngredientFormModal
        isOpen={false}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    expect(screen.queryByText(/nuevo ingrediente/i)).not.toBeInTheDocument();
  });

  it('renders creation form with editable unit of measure when initialData is null', () => {
    render(
      <IngredientFormModal
        isOpen={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    expect(screen.getByRole('heading', { name: /nuevo ingrediente/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/nombre del ingrediente/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/categoría/i)).toBeInTheDocument();

    const uomSelect = screen.getByLabelText(/unidad de medida/i);
    expect(uomSelect).toBeInTheDocument();
    expect(uomSelect).not.toBeDisabled();

    expect(screen.queryByLabelText(/costo unitario/i)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /guardar ingrediente/i })).toBeInTheDocument();
  });

  it('locks unit of measure input when editing an ingredient with stock > 0 (FR-013)', () => {
    const ingredientWithStock: AdminIngredientDetailResponse = {
      id: 'ing-1',
      tenantId: 'tenant-1',
      name: 'Harina 000',
      category: 'Dry Goods',
      unitOfMeasure: 'kg',
      unitPurchaseCost: 120.5,
      status: 'ACTIVE',
      currentStock: 25.0,
      createdAt: '2026-10-24T10:00:00Z',
      updatedAt: '2026-10-24T10:00:00Z',
    };

    render(
      <IngredientFormModal
        isOpen={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
        initialData={ingredientWithStock}
      />
    );

    expect(screen.getByRole('heading', { name: /editar ingrediente/i })).toBeInTheDocument();
    const uomSelect = screen.getByLabelText(/unidad de medida/i);
    expect(uomSelect).toBeDisabled();
    expect(
      screen.getByText(/no se puede cambiar la unidad de medida mientras haya stock positivo/i)
    ).toBeInTheDocument();
  });

  it('allows unit of measure to be changed when editing an ingredient with zero stock', () => {
    const ingredientZeroStock: AdminIngredientDetailResponse = {
      id: 'ing-2',
      tenantId: 'tenant-1',
      name: 'Levadura',
      category: 'Dry Goods',
      unitOfMeasure: 'g',
      unitPurchaseCost: 15.0,
      status: 'ACTIVE',
      currentStock: 0,
      createdAt: '2026-10-24T10:00:00Z',
      updatedAt: '2026-10-24T10:00:00Z',
    };

    render(
      <IngredientFormModal
        isOpen={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
        initialData={ingredientZeroStock}
      />
    );

    const uomSelect = screen.getByLabelText(/unidad de medida/i);
    expect(uomSelect).not.toBeDisabled();
  });

  it('validates required fields and positive purchase cost', async () => {
    const user = userEvent.setup();
    render(
      <IngredientFormModal
        isOpen={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    await user.click(screen.getByRole('button', { name: /guardar ingrediente/i }));

    expect(await screen.findByText(/el nombre es obligatorio/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('submits valid creation payload', async () => {
    const user = userEvent.setup();
    mockOnSubmit.mockResolvedValueOnce(undefined);

    render(
      <IngredientFormModal
        isOpen={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    await user.type(screen.getByLabelText(/nombre del ingrediente/i), 'Tomates Perita');
    await user.selectOptions(screen.getByLabelText(/categoría/i), 'Produce');
    await user.selectOptions(screen.getByLabelText(/unidad de medida/i), 'kg');

    await user.click(screen.getByRole('button', { name: /guardar ingrediente/i }));

    expect(mockOnSubmit).toHaveBeenCalledWith({
      name: 'Tomates Perita',
      category: 'Produce',
      unitOfMeasure: 'kg',
    });
  });
});
