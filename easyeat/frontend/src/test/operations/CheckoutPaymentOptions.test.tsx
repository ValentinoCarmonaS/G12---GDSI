import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CheckoutPaymentOptions } from '@/components/operations/CheckoutPaymentOptions';
import type { DinerPaymentMethod } from '@/types/operations';

const mercadoPago: DinerPaymentMethod = { paymentMethod: 'MERCADO_PAGO', displayName: 'Mercado Pago' };
const stripe: DinerPaymentMethod = { paymentMethod: 'STRIPE', displayName: 'Stripe' };

function props(overrides: Partial<React.ComponentProps<typeof CheckoutPaymentOptions>> = {}) {
  return {
    methods: [mercadoPago, stripe],
    onConfirm: vi.fn(),
    revalidate: vi.fn().mockResolvedValue(undefined),
    onRefresh: vi.fn(),
    ...overrides,
  };
}

describe('CheckoutPaymentOptions (INV-4, INV-9, EC-009, EC-010, EC-011)', () => {
  it('renders only the methods it is given: a disabled one is simply not there (INV-4)', () => {
    render(<CheckoutPaymentOptions {...props({ methods: [mercadoPago] })} />);

    expect(screen.getByRole('radio', { name: 'Mercado Pago' })).toBeInTheDocument();
    expect(screen.queryByRole('radio', { name: 'Stripe' })).not.toBeInTheDocument();
    expect(screen.queryByText('Stripe')).not.toBeInTheDocument();
  });

  it('shouldRenderAllDisabledMessage: no methods shows the blocking message and the confirm button is disabled (EC-009)', () => {
    render(<CheckoutPaymentOptions {...props({ methods: [] })} />);

    expect(screen.getByText('No hay medios de pago online disponibles en este momento.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /confirmar pedido/i })).toBeDisabled();
  });

  it('keeps the confirm button disabled until a method is chosen', async () => {
    render(<CheckoutPaymentOptions {...props()} />);

    expect(screen.getByRole('button', { name: /confirmar pedido/i })).toBeDisabled();
    await userEvent.click(screen.getByRole('radio', { name: 'Stripe' }));
    expect(screen.getByRole('button', { name: /confirmar pedido/i })).toBeEnabled();
  });

  it('shouldShowNewlyEnabledMethodOnRefresh: a method enabled meanwhile appears after refreshing (EC-011)', async () => {
    const base = props({ methods: [mercadoPago] });
    const { rerender } = render(<CheckoutPaymentOptions {...base} />);
    expect(screen.queryByRole('radio', { name: 'Stripe' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /actualizar opciones/i }));
    expect(base.onRefresh).toHaveBeenCalledTimes(1);
    rerender(<CheckoutPaymentOptions {...base} methods={[mercadoPago, stripe]} />);

    expect(screen.getByRole('radio', { name: 'Stripe' })).toBeInTheDocument();
  });

  it('drops the selection when the chosen method disappears from the list', async () => {
    const base = props();
    const { rerender } = render(<CheckoutPaymentOptions {...base} />);
    await userEvent.click(screen.getByRole('radio', { name: 'Stripe' }));

    rerender(<CheckoutPaymentOptions {...base} methods={[mercadoPago]} />);

    expect(screen.getByRole('button', { name: /confirmar pedido/i })).toBeDisabled();
  });

  it('revalidates the chosen method before confirming and then confirms it (INV-9)', async () => {
    const order: string[] = [];
    const revalidate = vi.fn().mockImplementation(async () => { order.push('revalidate'); });
    const onConfirm = vi.fn().mockImplementation(() => { order.push('confirm'); });
    render(<CheckoutPaymentOptions {...props({ revalidate, onConfirm })} />);

    await userEvent.click(screen.getByRole('radio', { name: 'Stripe' }));
    await userEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }));

    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith('STRIPE'));
    expect(revalidate).toHaveBeenCalledWith('STRIPE');
    expect(order).toEqual(['revalidate', 'confirm']);
  });

  it('a revalidation rejection blocks the order, explains it and refreshes the options (EC-010)', async () => {
    const revalidate = vi.fn().mockRejectedValue({ response: { status: 409 } });
    const base = props({ revalidate });
    render(<CheckoutPaymentOptions {...base} />);

    await userEvent.click(screen.getByRole('radio', { name: 'Stripe' }));
    await userEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El método de pago seleccionado ya no está disponible. Por favor elegí otro.'
    );
    expect(base.onConfirm).not.toHaveBeenCalled();
    expect(base.onRefresh).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: /confirmar pedido/i })).toBeDisabled();
  });

  it('shows a generic message for any other revalidation failure and does not confirm', async () => {
    const base = props({ revalidate: vi.fn().mockRejectedValue(new Error('network')) });
    render(<CheckoutPaymentOptions {...base} />);

    await userEvent.click(screen.getByRole('radio', { name: 'Mercado Pago' }));
    await userEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo verificar el método de pago. Intente nuevamente.');
    expect(base.onConfirm).not.toHaveBeenCalled();
    expect(base.onRefresh).not.toHaveBeenCalled();
  });
});
