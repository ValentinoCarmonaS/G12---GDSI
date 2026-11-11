import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ConcurrencyAlertModal } from '../../src/components/checkout/ConcurrencyAlertModal';

describe('T016: ConcurrencyAlertModal (AS-2, AS-3, CB-02, CB-03, FR-013)', () => {
  it('renders insufficient stock alert and modify cart action (AS-2, CB-02)', () => {
    const onModifyCart = vi.fn();
    const onClose = vi.fn();

    render(
      <ConcurrencyAlertModal
        isOpen={true}
        type="INSUFFICIENT_STOCK"
        message="Los insumos de uno o más platos se agotaron por alta demanda concurrente."
        unavailableDishes={[
          {
            dishId: 'dish-1',
            dishName: 'Hamburguesa Doble',
            limitingIngredient: 'Carne Picada',
          },
        ]}
        onAction={onModifyCart}
        onClose={onClose}
      />
    );

    expect(screen.getByText(/insumos agotados/i)).toBeInTheDocument();
    expect(screen.getByText(/hamburguesa doble/i)).toBeInTheDocument();
    expect(screen.getByText(/carne picada/i)).toBeInTheDocument();

    const modifyBtn = screen.getByRole('button', { name: /modificar carrito/i });
    fireEvent.click(modifyBtn);
    expect(onModifyCart).toHaveBeenCalledTimes(1);
  });

  it('renders slot saturation alert and select new slot action (AS-3, CB-03)', () => {
    const onSelectSlot = vi.fn();
    const onClose = vi.fn();

    render(
      <ConcurrencyAlertModal
        isOpen={true}
        type="SLOT_CAPACITY_EXCEEDED"
        message="La franja horaria seleccionada ha alcanzado su cupo máximo luego de expirar tu reserva."
        onAction={onSelectSlot}
        onClose={onClose}
      />
    );

    expect(screen.getByText(/horario no disponible|franja horaria saturada/i)).toBeInTheDocument();
    const selectSlotBtn = screen.getByRole('button', { name: /elegir otro horario|seleccionar horario/i });
    fireEvent.click(selectSlotBtn);
    expect(onSelectSlot).toHaveBeenCalledTimes(1);
  });
});
