import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RejectionDialog } from '../../../src/components/dishreview/RejectionDialog';

/**
 * FR-021 (10-1000 characters after trimming, never blank, with a live counter) and FR-031 (an
 * explicit way to abandon that touches nothing).
 *
 * Bulk text entry uses `fireEvent.change` rather than `userEvent.type`: typing a long string one
 * keystroke at a time is both slow and, under a loaded full-suite run, prone to characters
 * landing out of order as React's batching falls behind the simulated keystroke rate. Only the
 * one test about the live counter *while typing* needs real keystroke-by-keystroke behaviour, and
 * its string is short enough not to trigger either problem.
 */
describe('RejectionDialog', () => {
  it('renders nothing while closed', () => {
    render(<RejectionDialog isOpen={false} onConfirm={vi.fn()} onCancel={vi.fn()} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('shows a live character counter as the Administrator types', async () => {
    const user = userEvent.setup();
    render(<RejectionDialog isOpen onConfirm={vi.fn()} onCancel={vi.fn()} />);

    const textarea = screen.getByLabelText(/comentario/i);
    await user.type(textarea, 'Falta la foto');

    expect(screen.getByText(/13\s*\/\s*1000/)).toBeInTheDocument();
  });

  it('disables submission below the 10-character minimum', () => {
    render(<RejectionDialog isOpen onConfirm={vi.fn()} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/comentario/i), { target: { value: 'muy corto' } });

    expect(screen.getByRole('button', { name: /rechazar/i })).toBeDisabled();
  });

  it('enables submission at exactly 10 characters', () => {
    render(<RejectionDialog isOpen onConfirm={vi.fn()} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/comentario/i), { target: { value: '1234567890' } });

    expect(screen.getByRole('button', { name: /rechazar/i })).not.toBeDisabled();
  });

  it('disables submission on a whitespace-only comment, however long', () => {
    render(<RejectionDialog isOpen onConfirm={vi.fn()} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/comentario/i), {
      target: { value: '                    ' },
    });

    expect(screen.getByRole('button', { name: /rechazar/i })).toBeDisabled();
  });

  it('disables submission beyond 1000 characters and the counter reflects it', () => {
    render(<RejectionDialog isOpen onConfirm={vi.fn()} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/comentario/i), {
      target: { value: 'a'.repeat(1001) },
    });

    expect(screen.getByText(/1001\s*\/\s*1000/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /rechazar/i })).toBeDisabled();
  });

  it('calls onConfirm with the comment once a valid submission is clicked', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(<RejectionDialog isOpen onConfirm={onConfirm} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/comentario/i), {
      target: { value: 'Falta la foto del plato, por favor adjuntala.' },
    });
    await user.click(screen.getByRole('button', { name: /rechazar/i }));

    expect(onConfirm).toHaveBeenCalledWith('Falta la foto del plato, por favor adjuntala.');
  });

  it('renders markup in the comment as literal text, never interpreted', () => {
    render(<RejectionDialog isOpen onConfirm={vi.fn()} onCancel={vi.fn()} />);

    const textarea = screen.getByLabelText(/comentario/i) as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: '<b>urgente</b> revisar el costo' } });

    expect(textarea.value).toBe('<b>urgente</b> revisar el costo');
    expect(document.querySelector('b')).not.toBeInTheDocument();
  });

  it('cancelling calls onCancel and never onConfirm (FR-031)', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(<RejectionDialog isOpen onConfirm={onConfirm} onCancel={onCancel} />);

    fireEvent.change(screen.getByLabelText(/comentario/i), {
      target: { value: 'Un comentario cualquiera.' },
    });
    await user.click(screen.getByRole('button', { name: /cancelar/i }));

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
