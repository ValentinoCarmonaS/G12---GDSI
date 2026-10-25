import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AddToCartRiskModal } from '../../src/components/cart/AddToCartRiskModal';

describe('AddToCartRiskModal Component (T045, US3, FR-021, SC-005)', () => {
  it('does not render modal when isOpen is false', () => {
    const handleConfirm = vi.fn();
    const handleCancel = vi.fn();

    const { container } = render(
      <AddToCartRiskModal
        isOpen={false}
        dishName="Pastel de Papa"
        conflictingRestrictions={['DAIRY']}
        onConfirm={handleConfirm}
        onCancel={handleCancel}
      />
    );

    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId('add-to-cart-risk-modal')).not.toBeInTheDocument();
  });

  it('renders modal with dialog role, dish name, and conflicting badges when isOpen is true (FR-021)', () => {
    const handleConfirm = vi.fn();
    const handleCancel = vi.fn();

    render(
      <AddToCartRiskModal
        isOpen={true}
        dishName="Tarta de Maní y Nueces"
        conflictingRestrictions={['PEANUTS', 'TREE_NUTS']}
        onConfirm={handleConfirm}
        onCancel={handleCancel}
      />
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Advertencia de Seguridad Alimentaria')).toBeInTheDocument();
    expect(screen.getByText(/Tarta de Maní y Nueces/i)).toBeInTheDocument();
    expect(screen.getByText(/Contiene Maní/i)).toBeInTheDocument();
    expect(screen.getByText(/Contiene Frutos secos/i)).toBeInTheDocument();
  });

  it('keeps confirm button disabled until explicit risk acknowledgment checkbox is checked (SC-005)', () => {
    const handleConfirm = vi.fn();
    const handleCancel = vi.fn();

    render(
      <AddToCartRiskModal
        isOpen={true}
        dishName="Lasaña con Queso"
        conflictingRestrictions={['DAIRY']}
        onConfirm={handleConfirm}
        onCancel={handleCancel}
      />
    );

    const confirmBtn = screen.getByTestId('risk-modal-confirm-button');
    expect(confirmBtn).toBeDisabled();

    const checkbox = screen.getByTestId('risk-modal-acknowledge-checkbox');
    fireEvent.click(checkbox);

    expect(confirmBtn).not.toBeDisabled();

    fireEvent.click(confirmBtn);
    expect(handleConfirm).toHaveBeenCalledTimes(1);
    expect(handleCancel).not.toHaveBeenCalled();
  });

  it('triggers onCancel when Cancel button is clicked without invoking onConfirm (CHK034)', () => {
    const handleConfirm = vi.fn();
    const handleCancel = vi.fn();

    render(
      <AddToCartRiskModal
        isOpen={true}
        dishName="Sopa Tradicional"
        conflictingRestrictions={['CELIAC']}
        onConfirm={handleConfirm}
        onCancel={handleCancel}
      />
    );

    const cancelBtn = screen.getByTestId('risk-modal-cancel-button');
    fireEvent.click(cancelBtn);

    expect(handleCancel).toHaveBeenCalledTimes(1);
    expect(handleConfirm).not.toHaveBeenCalled();
  });
});
