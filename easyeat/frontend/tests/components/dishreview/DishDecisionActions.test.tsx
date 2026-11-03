import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DishDecisionActions } from '../../../src/components/dishreview/DishDecisionActions';
import { dishReviewApi } from '../../../src/api/dishReviewApi';
import type { DishReviewDetail } from '../../../src/types/dishReview';

vi.mock('../../../src/api/dishReviewApi', () => ({
  dishReviewApi: {
    setRetailPrice: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
    cancel: vi.fn(),
  },
}));

function detail(overrides: Partial<DishReviewDetail> = {}): DishReviewDetail {
  return {
    dishId: 'dish-1',
    name: 'Risotto de Hongos',
    description: null,
    category: 'Platos principales',
    status: 'PENDING_PRICE',
    retailPrice: null,
    photoUrl: null,
    authorName: 'Camila Duarte',
    authorDeactivated: false,
    allergensConfirmed: true,
    submittedAt: '2026-10-01T10:00:00Z',
    publishedAt: null,
    reviewCycleCount: 0,
    isResubmission: false,
    margin: {
      foodCost: 4500,
      retailPrice: null,
      marginPct: null,
      marginTolerancePp: null,
      toleranceThresholdPrice: null,
      hasUnpricedIngredients: false,
      marginDerivable: true,
    },
    publicationGate: {
      satisfied: true,
      unmetConditions: [],
      requiredAcknowledgements: [],
    },
    recipeAbsent: false,
    foodCostAudit: {
      dishId: 'dish-1',
      dishName: 'Risotto de Hongos',
      totalFoodCost: 4500,
      hasUnpricedIngredients: false,
      warningNotes: [],
      requiredDietaryRestrictions: [],
      items: [],
    },
    ...overrides,
  };
}

/**
 * Closes the gap `HANDOFF-post-merge-ui-gap.md` §2 found: `MarginPriceCalculator`,
 * `PublicationConfirmModal` and `RejectionDialog` each passed their own isolated test, but nothing
 * composed them against `dishReviewApi`'s three write methods. This suite exercises the real child
 * components (not stubs), so a prop mismatch between this container and any of the three would
 * fail here exactly as it would in the browser.
 */
describe('DishDecisionActions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('which controls render, by dish state (contract state gates)', () => {
    it('PENDING_PRICE renders the price calculator and both decision buttons', () => {
      render(<DishDecisionActions detail={detail()} onDecided={vi.fn()} />);

      expect(screen.getByLabelText(/precio de venta/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /aprobar y publicar/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /rechazar \/ devolver a cocina/i })).toBeInTheDocument();
    });

    it('PUBLISHED renders only the price calculator, for repricing (FR-013)', () => {
      render(
        <DishDecisionActions
          detail={detail({ status: 'PUBLISHED', retailPrice: 11250, publishedAt: '2026-10-02T10:00:00Z' })}
          onDecided={vi.fn()}
        />
      );

      expect(screen.getByLabelText(/precio de venta/i)).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /aprobar y publicar/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /rechazar \/ devolver a cocina/i })).not.toBeInTheDocument();
    });

    it('DRAFT renders nothing: a draft is priced and decided only once resubmitted', () => {
      const { container } = render(
        <DishDecisionActions detail={detail({ status: 'DRAFT' })} onDecided={vi.fn()} />
      );

      expect(container).toBeEmptyDOMElement();
    });

    it('CANCELLED renders no action controls because the state is terminal (FR-040)', () => {
      const { container } = render(
        <DishDecisionActions detail={detail({ status: 'CANCELLED' })} onDecided={vi.fn()} />
      );

      expect(container).toBeEmptyDOMElement();
    });
  });

  describe('setting the retail price (FR-009, FR-013, FR-025)', () => {
    it('saves with the previously-read price as expectedRetailPrice, null included on a first price', async () => {
      const user = userEvent.setup();
      vi.mocked(dishReviewApi.setRetailPrice).mockResolvedValue({
        dishId: 'dish-1',
        status: 'PENDING_PRICE',
        margin: detail().margin,
        priceHistoryEntryId: 'h-1',
      });
      const onDecided = vi.fn();
      render(<DishDecisionActions detail={detail({ retailPrice: null })} onDecided={onDecided} />);

      await user.type(screen.getByLabelText(/precio de venta/i), '9000');
      await user.click(screen.getByRole('button', { name: /guardar/i }));

      await waitFor(() =>
        expect(dishReviewApi.setRetailPrice).toHaveBeenCalledWith('dish-1', {
          retailPrice: 9000,
          expectedRetailPrice: null,
          acknowledgeNegativeMargin: false,
        })
      );
      expect(onDecided).toHaveBeenCalledTimes(1);
    });

    it('a stale-price conflict is shown and triggers a refresh, so the panel refetches the current price', async () => {
      const user = userEvent.setup();
      const error = Object.assign(new Error('Conflict'), {
        response: {
          status: 409,
          data: {
            type: 'https://easyeat.com/errors/dish-stale-price',
            detail: 'El precio del plato cambió desde que lo leíste.',
            currentRetailPrice: 9000,
          },
        },
      });
      vi.mocked(dishReviewApi.setRetailPrice).mockRejectedValue(error);
      const onDecided = vi.fn();
      render(
        <DishDecisionActions
          detail={detail({ retailPrice: 11250, margin: { ...detail().margin, retailPrice: 11250, marginPct: 60 } })}
          onDecided={onDecided}
        />
      );

      await user.click(screen.getByRole('button', { name: /guardar/i }));

      expect(await screen.findByTestId('price-error')).toHaveTextContent(/cambió/i);
      expect(onDecided).toHaveBeenCalledTimes(1);
    });

    it('a generic failure is shown without calling onDecided, since nothing about the read is known to be stale', async () => {
      const user = userEvent.setup();
      vi.mocked(dishReviewApi.setRetailPrice).mockRejectedValue(new Error('Network Error'));
      const onDecided = vi.fn();
      render(
        <DishDecisionActions
          detail={detail({ retailPrice: 11250, margin: { ...detail().margin, retailPrice: 11250, marginPct: 60 } })}
          onDecided={onDecided}
        />
      );

      await user.click(screen.getByRole('button', { name: /guardar/i }));

      expect(await screen.findByTestId('price-error')).toBeInTheDocument();
      expect(onDecided).not.toHaveBeenCalled();
    });
  });

  describe('approving (FR-015 to FR-019, FR-031)', () => {
    it('is disabled while the publication gate is unsatisfied', () => {
      render(
        <DishDecisionActions
          detail={detail({
            publicationGate: { satisfied: false, unmetConditions: ['EMPTY_RECIPE'], requiredAcknowledgements: [] },
          })}
          onDecided={vi.fn()}
        />
      );

      expect(screen.getByRole('button', { name: /aprobar y publicar/i })).toBeDisabled();
    });

    it('calls the API directly, with no acknowledgement flags, when none is required', async () => {
      const user = userEvent.setup();
      vi.mocked(dishReviewApi.approve).mockResolvedValue({
        dishId: 'dish-1',
        status: 'PUBLISHED',
        alreadyPublished: false,
        publishedAt: '2026-10-05T10:00:00Z',
        reviewHistoryEntryId: 'h-1',
      });
      const onDecided = vi.fn();
      render(<DishDecisionActions detail={detail()} onDecided={onDecided} />);

      await user.click(screen.getByRole('button', { name: /aprobar y publicar/i }));

      await waitFor(() => expect(dishReviewApi.approve).toHaveBeenCalledWith('dish-1', {}));
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      expect(onDecided).toHaveBeenCalledTimes(1);
    });

    it('opens the confirmation modal when acknowledgements are outstanding, without calling the API yet', async () => {
      const user = userEvent.setup();
      render(
        <DishDecisionActions
          detail={detail({
            publicationGate: {
              satisfied: true,
              unmetConditions: [],
              requiredAcknowledgements: ['UNPRICED_INGREDIENTS', 'NEGATIVE_MARGIN'],
            },
          })}
          onDecided={vi.fn()}
        />
      );

      await user.click(screen.getByRole('button', { name: /aprobar y publicar/i }));

      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(dishReviewApi.approve).not.toHaveBeenCalled();
    });

    it('maps each required acknowledgement to its own request flag on confirm', async () => {
      const user = userEvent.setup();
      vi.mocked(dishReviewApi.approve).mockResolvedValue({
        dishId: 'dish-1',
        status: 'PUBLISHED',
        alreadyPublished: false,
        publishedAt: '2026-10-05T10:00:00Z',
        reviewHistoryEntryId: 'h-1',
      });
      render(
        <DishDecisionActions
          detail={detail({
            publicationGate: {
              satisfied: true,
              unmetConditions: [],
              requiredAcknowledgements: ['UNPRICED_INGREDIENTS'],
            },
          })}
          onDecided={vi.fn()}
        />
      );

      await user.click(screen.getByRole('button', { name: /aprobar y publicar/i }));
      const dialog = screen.getByRole('dialog');
      await user.click(within(dialog).getByRole('button', { name: /confirmar/i }));

      await waitFor(() =>
        expect(dishReviewApi.approve).toHaveBeenCalledWith('dish-1', {
          acknowledgeUnpricedIngredients: true,
          acknowledgeNegativeMargin: false,
        })
      );
    });

    it('cancelling the confirmation modal never calls the API (FR-031)', async () => {
      const user = userEvent.setup();
      render(
        <DishDecisionActions
          detail={detail({
            publicationGate: {
              satisfied: true,
              unmetConditions: [],
              requiredAcknowledgements: ['NEGATIVE_MARGIN'],
            },
          })}
          onDecided={vi.fn()}
        />
      );

      await user.click(screen.getByRole('button', { name: /aprobar y publicar/i }));
      const dialog = screen.getByRole('dialog');
      await user.click(within(dialog).getByRole('button', { name: /cancelar/i }));

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      expect(dishReviewApi.approve).not.toHaveBeenCalled();
    });

    it('a 200 with alreadyPublished still calls onDecided, so the panel reflects the real publishedAt', async () => {
      const user = userEvent.setup();
      vi.mocked(dishReviewApi.approve).mockResolvedValue({
        dishId: 'dish-1',
        status: 'PUBLISHED',
        alreadyPublished: true,
        publishedAt: '2026-09-01T10:00:00Z',
        reviewHistoryEntryId: null,
      });
      const onDecided = vi.fn();
      render(<DishDecisionActions detail={detail()} onDecided={onDecided} />);

      await user.click(screen.getByRole('button', { name: /aprobar y publicar/i }));

      await waitFor(() => expect(onDecided).toHaveBeenCalledTimes(1));
    });

    it('a dish-already-decided conflict is shown and triggers a refresh', async () => {
      const user = userEvent.setup();
      const error = Object.assign(new Error('Conflict'), {
        response: {
          status: 409,
          data: {
            type: 'https://easyeat.com/errors/dish-already-decided',
            detail: 'Otro administrador ya decidió este plato.',
            currentStatus: 'DRAFT',
          },
        },
      });
      vi.mocked(dishReviewApi.approve).mockRejectedValue(error);
      const onDecided = vi.fn();
      render(<DishDecisionActions detail={detail()} onDecided={onDecided} />);

      await user.click(screen.getByRole('button', { name: /aprobar y publicar/i }));

      expect(await screen.findByTestId('approval-error')).toHaveTextContent(/ya decidió/i);
      expect(onDecided).toHaveBeenCalledTimes(1);
    });
  });

  describe('rejecting (FR-020 to FR-022, FR-031)', () => {
    it('opens the rejection dialog without calling the API yet', async () => {
      const user = userEvent.setup();
      render(<DishDecisionActions detail={detail()} onDecided={vi.fn()} />);

      await user.click(screen.getByRole('button', { name: /rechazar \/ devolver a cocina/i }));

      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(dishReviewApi.reject).not.toHaveBeenCalled();
    });

    it('submits the typed comment verbatim and calls onDecided on success', async () => {
      const user = userEvent.setup();
      vi.mocked(dishReviewApi.reject).mockResolvedValue({
        dishId: 'dish-1',
        status: 'DRAFT',
        cycleNumber: 1,
        reviewHistoryEntryId: 'h-1',
      });
      const onDecided = vi.fn();
      render(<DishDecisionActions detail={detail()} onDecided={onDecided} />);

      await user.click(screen.getByRole('button', { name: /rechazar \/ devolver a cocina/i }));
      const dialog = screen.getByRole('dialog');
      await user.type(within(dialog).getByLabelText(/comentario/i), 'Falta la foto del plato.');
      await user.click(within(dialog).getByRole('button', { name: 'Rechazar' }));

      await waitFor(() =>
        expect(dishReviewApi.reject).toHaveBeenCalledWith('dish-1', {
          comment: 'Falta la foto del plato.',
        })
      );
      expect(onDecided).toHaveBeenCalledTimes(1);
    });

    it('cancelling never calls the API (FR-031)', async () => {
      const user = userEvent.setup();
      render(<DishDecisionActions detail={detail()} onDecided={vi.fn()} />);

      await user.click(screen.getByRole('button', { name: /rechazar \/ devolver a cocina/i }));
      const dialog = screen.getByRole('dialog');
      await user.click(within(dialog).getByRole('button', { name: /cancelar/i }));

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      expect(dishReviewApi.reject).not.toHaveBeenCalled();
    });

    it('preloads the dish and forwards the edited proposal when the Administrator includes it (FR-042)', async () => {
      const user = userEvent.setup();
      vi.mocked(dishReviewApi.reject).mockResolvedValue({
        dishId: 'dish-1',
        status: 'DRAFT',
        cycleNumber: 1,
        reviewHistoryEntryId: 'h-1',
      });
      render(
        <DishDecisionActions
          detail={detail({
            foodCostAudit: {
              dishId: 'dish-1',
              dishName: 'Risotto de Hongos',
              totalFoodCost: 4500,
              hasUnpricedIngredients: false,
              warningNotes: [],
              requiredDietaryRestrictions: [],
              items: [
                {
                  ingredientId: 'ing-1',
                  ingredientName: 'Arroz Carnaroli',
                  recipeQuantity: 250,
                  unitOfMeasure: 'g',
                  unitPurchaseCost: 2,
                  lineSubtotal: 500,
                  unpricedWarning: false,
                },
              ],
            },
          })}
          onDecided={vi.fn()}
        />
      );

      await user.click(screen.getByRole('button', { name: /rechazar \/ devolver a cocina/i }));
      const dialog = screen.getByRole('dialog');
      await user.type(within(dialog).getByLabelText(/comentario/i), 'Ajustá la propuesta y reenviá.');
      await user.click(within(dialog).getByLabelText(/incluir propuesta de cambios/i));

      expect(within(dialog).getByLabelText(/título propuesto/i)).toHaveValue('Risotto de Hongos');
      expect(within(dialog).getByText('Arroz Carnaroli')).toBeInTheDocument();
      // The proposal prefill reads the audit line's `recipeQuantity`; a stale `quantity` key
      // would seed the input with 0 or NaN instead of the real amount.
      expect(within(dialog).getByLabelText(/cantidad de arroz carnaroli/i)).toHaveValue(250);

      await user.click(within(dialog).getByRole('button', { name: 'Rechazar' }));

      await waitFor(() =>
        expect(dishReviewApi.reject).toHaveBeenCalledWith('dish-1', {
          comment: 'Ajustá la propuesta y reenviá.',
          proposedChanges: {
            title: 'Risotto de Hongos',
            description: null,
            allergenCodes: [],
            dietaryLifestyleCodes: [],
            ingredients: [{ ingredientId: 'ing-1', quantity: 250 }],
          },
        })
      );
    });

    it('a dish-already-decided conflict is shown and triggers a refresh', async () => {
      const user = userEvent.setup();
      const error = Object.assign(new Error('Conflict'), {
        response: {
          status: 409,
          data: {
            type: 'https://easyeat.com/errors/dish-already-decided',
            detail: 'Otro administrador ya decidió este plato.',
            currentStatus: 'PUBLISHED',
          },
        },
      });
      vi.mocked(dishReviewApi.reject).mockRejectedValue(error);
      const onDecided = vi.fn();
      render(<DishDecisionActions detail={detail()} onDecided={onDecided} />);

      await user.click(screen.getByRole('button', { name: /rechazar \/ devolver a cocina/i }));
      const dialog = screen.getByRole('dialog');
      await user.type(within(dialog).getByLabelText(/comentario/i), 'Falta la foto del plato.');
      await user.click(within(dialog).getByRole('button', { name: 'Rechazar' }));

      expect(await screen.findByTestId('rejection-error')).toHaveTextContent(/ya decidió/i);
      expect(onDecided).toHaveBeenCalledTimes(1);
    });
  });

  describe('cancelling a pending dish (C-02)', () => {
    it('does not call the cancellation API when the confirmation is abandoned', async () => {
      const user = userEvent.setup();
      render(<DishDecisionActions detail={detail()} onDecided={vi.fn()} onCancelled={vi.fn()} />);

      await user.click(screen.getByRole('button', { name: /borrar/i }));
      const dialog = screen.getByRole('dialog', { name: /confirmar borrado/i });
      await user.click(within(dialog).getByRole('button', { name: /cancelar/i }));

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      expect(dishReviewApi.cancel).not.toHaveBeenCalled();
    });

    it('calls cancellation and notifies the page to clear the cancelled detail', async () => {
      const user = userEvent.setup();
      vi.mocked(dishReviewApi.cancel).mockResolvedValue(undefined);
      const onCancelled = vi.fn();
      const onDecided = vi.fn();
      render(<DishDecisionActions detail={detail()} onDecided={onDecided} onCancelled={onCancelled} />);

      await user.click(screen.getByRole('button', { name: /borrar/i }));
      const dialog = screen.getByRole('dialog', { name: /confirmar borrado/i });
      await user.click(within(dialog).getByRole('button', { name: /confirmar/i }));

      await waitFor(() => expect(dishReviewApi.cancel).toHaveBeenCalledWith('dish-1'));
      expect(onCancelled).toHaveBeenCalledTimes(1);
      expect(onDecided).not.toHaveBeenCalled();
    });
  });
});
