import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PublicationConfirmModal } from '../../../src/components/dishreview/PublicationConfirmModal';
import type { AcknowledgementKind } from '../../../src/types/dishReview';

/**
 * FR-017 (the advisory content, named per acknowledgement kind) and FR-031 (the required second
 * confirmation, and that abandoning it is a true no-op: nothing is confirmed, nothing changes).
 */
describe('PublicationConfirmModal', () => {
  it('renders nothing while closed', () => {
    render(
      <PublicationConfirmModal
        isOpen={false}
        requiredAcknowledgements={['NEGATIVE_MARGIN']}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('states the unpriced-ingredients advisory by name', () => {
    render(
      <PublicationConfirmModal
        isOpen
        requiredAcknowledgements={['UNPRICED_INGREDIENTS']}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    expect(screen.getByText(/insumo sin costo de adquisición/i)).toBeInTheDocument();
    expect(screen.queryByText(/margen.*negativo/i)).not.toBeInTheDocument();
  });

  it('states the negative-margin advisory by name', () => {
    render(
      <PublicationConfirmModal
        isOpen
        requiredAcknowledgements={['NEGATIVE_MARGIN']}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    expect(screen.getByText(/margen comercial.*es negativo/i)).toBeInTheDocument();
    expect(screen.queryByText(/insumo sin costo/i)).not.toBeInTheDocument();
  });

  it('states both advisories together when both apply', () => {
    const both: AcknowledgementKind[] = ['UNPRICED_INGREDIENTS', 'NEGATIVE_MARGIN'];
    render(
      <PublicationConfirmModal
        isOpen
        requiredAcknowledgements={both}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    expect(screen.getByText(/insumo sin costo de adquisición/i)).toBeInTheDocument();
    expect(screen.getByText(/margen comercial.*es negativo/i)).toBeInTheDocument();
  });

  it('requires the explicit second confirmation before calling onConfirm', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(
      <PublicationConfirmModal
        isOpen
        requiredAcknowledgements={['NEGATIVE_MARGIN']}
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />
    );

    expect(onConfirm).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: /confirmar/i }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('cancelling calls onCancel and never onConfirm, leaving the dish untouched (FR-031)', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(
      <PublicationConfirmModal
        isOpen
        requiredAcknowledgements={['NEGATIVE_MARGIN']}
        onConfirm={onConfirm}
        onCancel={onCancel}
      />
    );

    await user.click(screen.getByRole('button', { name: /cancelar/i }));

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
