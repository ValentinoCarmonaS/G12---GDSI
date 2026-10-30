import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MarginPriceCalculator } from '../../../src/components/dishreview/MarginPriceCalculator';
import type { MarginSummary } from '../../../src/types/dishReview';

function margin(overrides: Partial<MarginSummary> = {}): MarginSummary {
  return {
    foodCost: 4500,
    retailPrice: 11250,
    marginPct: 60,
    marginTolerancePp: 0.01,
    toleranceThresholdPrice: 20,
    hasUnpricedIngredients: false,
    marginDerivable: true,
    ...overrides,
  };
}

/**
 * FR-007 (reactive bidirectional updates), FR-008/SC-002 (disclosing the server-computed
 * precision rather than asserting ±0.01 pp unconditionally), FR-014/FR-031 (the negative-margin
 * confirmation, with an explicit way to abandon that touches nothing), and FR-030's disabled
 * states (the Food Cost 0.00 case, and the derived field locked while its counterpart is being
 * typed).
 */
describe('MarginPriceCalculator', () => {
  it('reactively derives the price as the margin is typed', async () => {
    const user = userEvent.setup();
    render(<MarginPriceCalculator margin={margin()} onConfirm={vi.fn()} />);

    const marginInput = screen.getByLabelText(/margen comercial/i);
    await user.clear(marginInput);
    await user.type(marginInput, '60');

    expect(screen.getByLabelText(/precio de venta/i)).toHaveValue('11250.00');
  });

  it('reactively derives the margin as the price is typed', async () => {
    const user = userEvent.setup();
    render(<MarginPriceCalculator margin={margin()} onConfirm={vi.fn()} />);

    const priceInput = screen.getByLabelText(/precio de venta/i);
    await user.clear(priceInput);
    await user.type(priceInput, '9000');

    expect(screen.getByLabelText(/margen comercial/i)).toHaveValue('50.00');
  });

  it('locks the derived field while the other is being edited, and unlocks it on blur (FR-030)', async () => {
    const user = userEvent.setup();
    render(<MarginPriceCalculator margin={margin()} onConfirm={vi.fn()} />);

    const marginInput = screen.getByLabelText(/margen comercial/i);
    const priceInput = screen.getByLabelText(/precio de venta/i);

    await user.clear(marginInput);
    await user.type(marginInput, '6');
    expect(priceInput).toBeDisabled();

    await user.tab();
    expect(priceInput).not.toBeDisabled();
  });

  it('disables the margin -> price direction entirely when the Food Cost is 0.00', () => {
    render(
      <MarginPriceCalculator
        margin={margin({ foodCost: 0, marginDerivable: false, marginPct: null, retailPrice: null })}
        onConfirm={vi.fn()}
      />
    );

    expect(screen.getByLabelText(/margen comercial/i)).toBeDisabled();
    expect(screen.getByText(/no se puede calcular un margen sin food cost/i)).toBeInTheDocument();
  });

  it('discloses the server-computed achievable deviation below the precision threshold (SC-002)', () => {
    // research.md §3.1's own pinned case: a Food Cost of 0.07 at a 60% target margin derives
    // 0.18, below the 20.00 threshold a 60% margin implies, with an observed deviation of
    // 1.1111 pp rather than the ±0.01 pp bound.
    render(
      <MarginPriceCalculator
        margin={margin({
          foodCost: 0.07,
          retailPrice: 0.18,
          marginPct: 61.1111,
          toleranceThresholdPrice: 20,
          marginTolerancePp: 1.1111,
        })}
        onConfirm={vi.fn()}
      />
    );

    expect(screen.getByText(/1,11/)).toBeInTheDocument();
    expect(screen.queryByText(/±0[.,]01/)).not.toBeInTheDocument();
  });

  it('does not disclose a deviation at or above the threshold price', () => {
    render(
      <MarginPriceCalculator
        margin={margin({ retailPrice: 11250, toleranceThresholdPrice: 20 })}
        onConfirm={vi.fn()}
      />
    );

    expect(screen.queryByTestId('tolerance-disclosure')).not.toBeInTheDocument();
  });

  it('saves immediately when the margin is not negative', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(<MarginPriceCalculator margin={margin()} onConfirm={onConfirm} />);

    await user.click(screen.getByRole('button', { name: /guardar/i }));

    expect(onConfirm).toHaveBeenCalledWith(11250, false);
  });

  describe('the negative-margin advisory (FR-014, FR-031)', () => {
    it('blocks the save and shows the advisory, naming the specific condition', async () => {
      const user = userEvent.setup();
      const onConfirm = vi.fn();
      render(
        <MarginPriceCalculator
          margin={margin({ retailPrice: 100, marginPct: -4400 })}
          onConfirm={onConfirm}
        />
      );

      await user.click(screen.getByRole('button', { name: /guardar/i }));

      expect(onConfirm).not.toHaveBeenCalled();
      expect(
        screen.getByText(/el precio ingresado es menor al costo de producción/i)
      ).toBeInTheDocument();
    });

    it('confirming the advisory saves with the acknowledgement set', async () => {
      const user = userEvent.setup();
      const onConfirm = vi.fn();
      render(
        <MarginPriceCalculator
          margin={margin({ retailPrice: 100, marginPct: -4400 })}
          onConfirm={onConfirm}
        />
      );

      await user.click(screen.getByRole('button', { name: /guardar/i }));
      await user.click(screen.getByRole('button', { name: /confirmar/i }));

      expect(onConfirm).toHaveBeenCalledWith(100, true);
    });

    it('abandoning the advisory touches nothing: no confirm call, price unchanged', async () => {
      const user = userEvent.setup();
      const onConfirm = vi.fn();
      render(
        <MarginPriceCalculator
          margin={margin({ retailPrice: 100, marginPct: -4400 })}
          onConfirm={onConfirm}
        />
      );

      await user.click(screen.getByRole('button', { name: /guardar/i }));
      await user.click(screen.getByRole('button', { name: /cancelar/i }));

      expect(onConfirm).not.toHaveBeenCalled();
      expect(screen.getByLabelText(/precio de venta/i)).toHaveValue('100.00');
      expect(
        screen.queryByText(/el precio ingresado es menor al costo de producción/i)
      ).not.toBeInTheDocument();
    });
  });
});
