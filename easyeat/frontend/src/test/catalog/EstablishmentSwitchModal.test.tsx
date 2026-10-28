import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';
import { EstablishmentSwitchModal } from '@/components/catalog/EstablishmentSwitchModal';
import { mockEstablishmentSummaryActive } from '@/test/mocks/dinerCatalogHandlers';

describe('EstablishmentSwitchModal Component Tests (T019, COM-1.5.1 CA 5)', () => {
  it('does not render when isOpen is false', () => {
    const { container } = render(
      <EstablishmentSwitchModal
        isOpen={false}
        targetEstablishment={mockEstablishmentSummaryActive}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders title, target establishment name, and cart warning when open', () => {
    render(
      <EstablishmentSwitchModal
        isOpen={true}
        targetEstablishment={mockEstablishmentSummaryActive}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    expect(screen.getByText('¿Cambiar de restaurante?')).toBeInTheDocument();
    expect(screen.getByText(mockEstablishmentSummaryActive.name)).toBeInTheDocument();
    expect(screen.getByText(/se vaciará tu carrito/i)).toBeInTheDocument();
  });

  it('calls onConfirm when clicking "Cambiar de local"', () => {
    const onConfirmMock = vi.fn();
    render(
      <EstablishmentSwitchModal
        isOpen={true}
        targetEstablishment={mockEstablishmentSummaryActive}
        onConfirm={onConfirmMock}
        onCancel={vi.fn()}
      />
    );

    const confirmButton = screen.getByRole('button', { name: /cambiar de local/i });
    fireEvent.click(confirmButton);
    expect(onConfirmMock).toHaveBeenCalledTimes(1);
  });

  it('calls onCancel when clicking "Cancelar"', () => {
    const onCancelMock = vi.fn();
    render(
      <EstablishmentSwitchModal
        isOpen={true}
        targetEstablishment={mockEstablishmentSummaryActive}
        onConfirm={vi.fn()}
        onCancel={onCancelMock}
      />
    );

    const cancelButton = screen.getByRole('button', { name: /cancelar/i });
    fireEvent.click(cancelButton);
    expect(onCancelMock).toHaveBeenCalledTimes(1);
  });
});
