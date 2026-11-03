import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';
import { FulfillmentModeSelector } from '../../components/checkout/FulfillmentModeSelector';
import { FulfillmentMode } from '../../types/order';

describe('FulfillmentModeSelector Component (T013, US1)', () => {
  it('renders both options unselected or with provided selection', () => {
    const onSelectMode = vi.fn();

    render(
      <FulfillmentModeSelector
        selectedMode={null}
        onSelectMode={onSelectMode}
        isImmediateAvailable={true}
      />
    );

    expect(screen.getByText(/Lo antes posible/i)).toBeInTheDocument();
    expect(screen.getByText(/Para más tarde/i)).toBeInTheDocument();
  });

  it('selects IMMEDIATE mode exclusively when clicked and invokes callback', () => {
    const onSelectMode = vi.fn();

    render(
      <FulfillmentModeSelector
        selectedMode={null}
        onSelectMode={onSelectMode}
        isImmediateAvailable={true}
      />
    );

    const immediateCard = screen.getByRole('radio', { name: /Lo antes posible/i });
    fireEvent.click(immediateCard);

    expect(onSelectMode).toHaveBeenCalledWith('IMMEDIATE');
  });

  it('selects SCHEDULED mode exclusively when clicked and invokes callback', () => {
    const onSelectMode = vi.fn();

    render(
      <FulfillmentModeSelector
        selectedMode="IMMEDIATE"
        onSelectMode={onSelectMode}
        isImmediateAvailable={true}
      />
    );

    const scheduledCard = screen.getByRole('radio', { name: /Para más tarde/i });
    fireEvent.click(scheduledCard);

    expect(onSelectMode).toHaveBeenCalledWith('SCHEDULED');
  });

  it('disables IMMEDIATE option and shows notice when current slot is at 100% capacity', () => {
    const onSelectMode = vi.fn();

    render(
      <FulfillmentModeSelector
        selectedMode={null}
        onSelectMode={onSelectMode}
        isImmediateAvailable={false}
        immediateUnavailableReason="Cocina al límite de capacidad actual — Seleccioná una reserva programada"
      />
    );

    const immediateRadio = screen.getByRole('radio', { name: /Lo antes posible/i });
    expect(immediateRadio).toBeDisabled();

    expect(
      screen.getByText(/Cocina al límite de capacidad actual — Seleccioná una reserva programada/i)
    ).toBeInTheDocument();

    // Clicking disabled immediate does not trigger onSelectMode
    fireEvent.click(immediateRadio);
    expect(onSelectMode).not.toHaveBeenCalled();
  });

  it('highlights the currently selected mode card', () => {
    const onSelectMode = vi.fn();

    const { rerender } = render(
      <FulfillmentModeSelector
        selectedMode={'IMMEDIATE' as FulfillmentMode}
        onSelectMode={onSelectMode}
        isImmediateAvailable={true}
      />
    );

    const immediateRadio = screen.getByRole('radio', { name: /Lo antes posible/i });
    expect(immediateRadio).toBeChecked();

    rerender(
      <FulfillmentModeSelector
        selectedMode={'SCHEDULED' as FulfillmentMode}
        onSelectMode={onSelectMode}
        isImmediateAvailable={true}
      />
    );

    const scheduledRadio = screen.getByRole('radio', { name: /Para más tarde/i });
    expect(scheduledRadio).toBeChecked();
    expect(immediateRadio).not.toBeChecked();
  });
});
