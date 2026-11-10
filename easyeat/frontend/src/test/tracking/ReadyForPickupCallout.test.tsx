import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ReadyForPickupCallout } from '../../components/tracking/ReadyForPickupCallout';

describe('T030 [US2]: ReadyForPickupCallout Component Tests', () => {
  it('renders prominent order ticket number #142 and green callout (FR-009, AS-8)', () => {
    render(<ReadyForPickupCallout ticketNumber={142} />);

    const callout = screen.getByTestId('ready-for-pickup-callout');
    expect(callout).toBeInTheDocument();
    expect(callout.className).toContain('green');

    // Prominent ticket number
    expect(screen.getByText(/#142/)).toBeInTheDocument();
    expect(screen.getByText(/listo para retirar/i)).toBeInTheDocument();
  });

  it('strictly suppresses remaining time estimates and progress countdowns (D-05, INV-008, CB-008)', () => {
    render(<ReadyForPickupCallout ticketNumber={142} message="Tu pedido está listo" />);

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.queryByText(/min restantes/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/tiempo estimado/i)).not.toBeInTheDocument();
    expect(screen.queryByTestId('active-proximity-countdown')).not.toBeInTheDocument();
  });
});
