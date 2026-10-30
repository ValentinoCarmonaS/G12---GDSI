import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RejectionDialog } from '../../../src/components/dishreview/RejectionDialog';

/**
 * FR-021 (10-1000 characters after trimming, never blank, with a live counter) and FR-031 (an
 * explicit way to abandon that touches nothing).
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

  it('disables submission below the 10-character minimum', async () => {
    const user = userEvent.setup();
    render(<RejectionDialog isOpen onConfirm={vi.fn()} onCancel={vi.fn()} />);

    await user.type(screen.getByLabelText(/comentario/i), 'muy corto');

    expect(screen.getByRole('button', { name: /rechazar/i })).toBeDisabled();
  });

  it('enables submission at exactly 10 characters', async () => {
    const user = userEvent.setup();
    render(<RejectionDialog isOpen onConfirm={vi.fn()} onCancel={vi.fn()} />);

    await user.type(screen.getByLabelText(/comentario/i), '1234567890');

    expect(screen.getByRole('button', { name: /rechazar/i })).not.toBeDisabled();
  });

  it('disables submission on a whitespace-only comment, however long', async () => {
    const user = userEvent.setup();
    render(<RejectionDialog isOpen onConfirm={vi.fn()} onCancel={vi.fn()} />);

    await user.type(screen.getByLabelText(/comentario/i), '                    ');

    expect(screen.getByRole('button', { name: /rechazar/i })).toBeDisabled();
  });

  it('disables submission beyond 1000 characters and the counter reflects it', async () => {
    const user = userEvent.setup();
    render(<RejectionDialog isOpen onConfirm={vi.fn()} onCancel={vi.fn()} />);

    await user.type(screen.getByLabelText(/comentario/i), 'a'.repeat(1001));

    expect(screen.getByText(/1001\s*\/\s*1000/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /rechazar/i })).toBeDisabled();
  });

  it('calls onConfirm with the comment once a valid submission is clicked', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(<RejectionDialog isOpen onConfirm={onConfirm} onCancel={vi.fn()} />);

    await user.type(screen.getByLabelText(/comentario/i),
        'Falta la foto del plato, por favor adjuntala.');
    await user.click(screen.getByRole('button', { name: /rechazar/i }));

    expect(onConfirm).toHaveBeenCalledWith('Falta la foto del plato, por favor adjuntala.');
  });

  it('renders markup in the comment as literal text, never interpreted', async () => {
    const user = userEvent.setup();
    render(<RejectionDialog isOpen onConfirm={vi.fn()} onCancel={vi.fn()} />);

    const textarea = screen.getByLabelText(/comentario/i) as HTMLTextAreaElement;
    await user.type(textarea, '<b>urgente</b> revisar el costo');

    expect(textarea.value).toBe('<b>urgente</b> revisar el costo');
    expect(document.querySelector('b')).not.toBeInTheDocument();
  });

  it('cancelling calls onCancel and never onConfirm (FR-031)', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(<RejectionDialog isOpen onConfirm={onConfirm} onCancel={onCancel} />);

    await user.type(screen.getByLabelText(/comentario/i), 'Un comentario cualquiera.');
    await user.click(screen.getByRole('button', { name: /cancelar/i }));

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
