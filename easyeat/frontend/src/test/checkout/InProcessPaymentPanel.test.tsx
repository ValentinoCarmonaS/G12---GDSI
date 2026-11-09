import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { InProcessPaymentPanel } from '../../components/checkout/InProcessPaymentPanel';

describe('T022: InProcessPaymentPanel Component Tests (AS-5, FR-010, Q3)', () => {
  const onResumeMock = vi.fn();
  const onCancelMock = vi.fn();

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
});
