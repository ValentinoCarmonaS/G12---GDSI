import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PaymentMethodToggles } from '@/components/operations/PaymentMethodToggles';
import type { AdminPaymentMethod } from '@/types/operations';

const methods: AdminPaymentMethod[] = [
  { id: 'pm-1', paymentMethod: 'MERCADO_PAGO', displayName: 'Mercado Pago', isEnabled: true, version: 4 },
  { id: null, paymentMethod: 'STRIPE', displayName: 'Stripe', isEnabled: true, version: null },
];

function setup(overrides: Partial<React.ComponentProps<typeof PaymentMethodToggles>> = {}) {
  const props = {
    methods,
    onToggle: vi.fn().mockResolvedValue(undefined),
    onReload: vi.fn(),
    ...overrides,
  };
  render(<PaymentMethodToggles {...props} />);
  return props;
}

describe('PaymentMethodToggles (ADM-2.2.3, FR-010, EC-012)', () => {
  it('renders one switch per method that reflects its state', () => {
    setup({
      methods: [
        { ...methods[0], isEnabled: false },
        methods[1],
      ],
    });

    expect(screen.getAllByRole('switch')).toHaveLength(2);
    expect(screen.getByRole('switch', { name: 'Mercado Pago' })).toHaveAttribute('aria-checked', 'false');
    expect(screen.getByRole('switch', { name: 'Stripe' })).toHaveAttribute('aria-checked', 'true');
  });

  it('toggling calls onToggle(method, enabled, version) with the version the admin saw', async () => {
    const props = setup();

    await userEvent.click(screen.getByRole('switch', { name: 'Mercado Pago' }));

    await waitFor(() => expect(props.onToggle).toHaveBeenCalledWith('MERCADO_PAGO', false, 4));
  });

  it('a method without a row (enabled, no version) is toggled with a null version', async () => {
    const props = setup();

    await userEvent.click(screen.getByRole('switch', { name: 'Stripe' }));

    await waitFor(() => expect(props.onToggle).toHaveBeenCalledWith('STRIPE', false, null));
  });

  it('keeps the switch disabled while the change is being saved', async () => {
    let finish: () => void = () => {};
    const onToggle = vi.fn().mockImplementation(() => new Promise<void>((resolve) => { finish = resolve; }));
    setup({ onToggle });

    await userEvent.click(screen.getByRole('switch', { name: 'Mercado Pago' }));
    expect(screen.getByRole('switch', { name: 'Mercado Pago' })).toBeDisabled();
    finish();
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Mercado Pago' })).toBeEnabled());
  });

  it('surfaces a 409 as a reload message and offers to reload (EC-012)', async () => {
    const props = setup({ onToggle: vi.fn().mockRejectedValue({ response: { status: 409 } }) });

    await userEvent.click(screen.getByRole('switch', { name: 'Mercado Pago' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'La configuración fue modificada por otro administrador. Recargue la pantalla para ver los cambios.'
    );
    await userEvent.click(screen.getByRole('button', { name: /recargar/i }));
    expect(props.onReload).toHaveBeenCalledTimes(1);
  });

  it('shows a generic error for any other failure', async () => {
    setup({ onToggle: vi.fn().mockRejectedValue(new Error('network')) });

    await userEvent.click(screen.getByRole('switch', { name: 'Stripe' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo guardar el método de pago. Intente nuevamente.');
  });

  it('warns the admin when every method is switched off (EC-009)', () => {
    setup({ methods: methods.map((method) => ({ ...method, isEnabled: false })) });

    expect(screen.getByText(/ningún método de pago online habilitado/i)).toBeInTheDocument();
  });
});
