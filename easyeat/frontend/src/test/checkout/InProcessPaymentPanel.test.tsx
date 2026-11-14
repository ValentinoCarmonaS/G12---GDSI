import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { InProcessPaymentPanel } from '../../components/checkout/InProcessPaymentPanel';

describe('T022: InProcessPaymentPanel Component Tests (AS-5, FR-010, Q3)', () => {
  const onResumeMock = vi.fn();
  const onCancelMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  const defaultProps = {
    redirectUrl: 'https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-123',
    onResumePayment: onResumeMock,
    onCancelAttempt: onCancelMock,
    isCancelling: false,
  };

  it('renders information panel indicating payment in process without any countdown timer (Q3)', () => {
    render(<InProcessPaymentPanel {...defaultProps} />);

    expect(screen.getByText(/pago en curso/i)).toBeInTheDocument();
    expect(screen.getByText(/tenés un pago pendiente de confirmación/i)).toBeInTheDocument();

    // Validar expresamente que NO existe cuenta regresiva en pantalla (Q3)
    expect(screen.queryByText(/tiempo restante/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/:[0-9]{2}/)).not.toBeInTheDocument();
  });

  it('provides button to resume payment and triggers callback or navigation (AS-5)', () => {
    render(<InProcessPaymentPanel {...defaultProps} />);

    const resumeBtn = screen.getByRole('button', { name: /retomar pago/i });
    expect(resumeBtn).toBeInTheDocument();

    fireEvent.click(resumeBtn);
    expect(onResumeMock).toHaveBeenCalledTimes(1);
  });

  it('provides button to voluntarily cancel payment attempt and triggers callback (AS-5, Q3)', () => {
    render(<InProcessPaymentPanel {...defaultProps} />);

    const cancelBtn = screen.getByRole('button', { name: /cancelar intento/i });
    expect(cancelBtn).toBeInTheDocument();

    fireEvent.click(cancelBtn);
    expect(onCancelMock).toHaveBeenCalledTimes(1);
  });

  it('disables cancel button and displays spinner when isCancelling is true', () => {
    render(<InProcessPaymentPanel {...defaultProps} isCancelling={true} />);

    const cancelBtn = screen.getByRole('button', { name: /cancelando/i });
    expect(cancelBtn).toBeDisabled();
  });

  it('Q5: hides or disables "Retomar pago", informs missing preference and keeps "Cancelar intento" enabled when redirectUrl is null or blank', () => {
    const { rerender } = render(
      <InProcessPaymentPanel
        redirectUrl={null}
        onCancelAttempt={onCancelMock}
        isCancelling={false}
      />
    );

    const resumeWithNull = screen.queryByRole('button', { name: /retomar pago/i });
    expect(resumeWithNull === null || (resumeWithNull as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText(/no existe preferencia de pago activa/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancelar intento/i })).toBeEnabled();

    rerender(
      <InProcessPaymentPanel
        redirectUrl="   "
        onResumePayment={onResumeMock}
        onCancelAttempt={onCancelMock}
        isCancelling={false}
      />
    );

    const resumeWithBlank = screen.queryByRole('button', { name: /retomar pago/i });
    expect(resumeWithBlank === null || (resumeWithBlank as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText(/no existe preferencia de pago activa/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancelar intento/i })).toBeEnabled();
  });

  it('uses high-contrast light surface classes readable over white checkout background', () => {
    render(<InProcessPaymentPanel {...defaultProps} />);

    const region = screen.getByRole('region', { name: /panel de pago en curso/i });
    expect(region.className).not.toMatch(/bg-amber-950|text-white/);
    expect(region.className).toMatch(/bg-amber-50|bg-white/);

    const heading = screen.getByText(/pago en curso/i);
    expect(heading.className).not.toMatch(/text-amber-200|text-white/);
    expect(heading.className).toMatch(/text-amber-9|text-gray-9/);
  });
});
