import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PendingEmailBanner } from '@/components/profile/PendingEmailBanner';

describe('PendingEmailBanner Component (T015, COM-1.4.1)', () => {
  const mockOnResend = vi.fn().mockResolvedValue(undefined);
  const mockOnCancel = vi.fn().mockResolvedValue(undefined);

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    // The cooldown-timer test below enables fake timers and restores them inline, but only on
    // its own success path: a failed assertion between vi.useFakeTimers() and vi.useRealTimers()
    // would leak fake timers into every test file that runs afterward in the same worker,
    // breaking userEvent's internal delays there. Restoring here is unconditional.
    vi.useRealTimers();
  });

  it('renders pending status banner showing proposed email', () => {
    render(
      <PendingEmailBanner
        pendingEmail="nuevo.correo@example.com"
        onResend={mockOnResend}
        onCancel={mockOnCancel}
        initialCooldownSeconds={0}
      />
    );

    expect(screen.getByText(/nuevo.correo@example.com/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reenviar/i })).toBeEnabled();
    expect(screen.getByRole('button', { name: /cancelar solicitud/i })).toBeInTheDocument();
  });

  it('disables resend button and shows reactive countdown timer when cooldown is active', () => {
    vi.useFakeTimers();

    render(
      <PendingEmailBanner
        pendingEmail="nuevo.correo@example.com"
        onResend={mockOnResend}
        onCancel={mockOnCancel}
        initialCooldownSeconds={60}
      />
    );

    const resendBtn = screen.getByRole('button', { name: /reenviar/i });
    expect(resendBtn).toBeDisabled();
    expect(screen.getByText(/60s/i)).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(10000); // 10 seconds pass
    });

    expect(screen.getByText(/50s/i)).toBeInTheDocument();
    expect(resendBtn).toBeDisabled();

    act(() => {
      vi.advanceTimersByTime(50000); // remaining 50s pass
    });

    expect(resendBtn).toBeEnabled();
    vi.useRealTimers();
  });

  it('triggers onResend callback when enabled resend button is clicked', async () => {
    const user = userEvent.setup();
    render(
      <PendingEmailBanner
        pendingEmail="nuevo.correo@example.com"
        onResend={mockOnResend}
        onCancel={mockOnCancel}
        initialCooldownSeconds={0}
      />
    );

    const resendBtn = screen.getByRole('button', { name: /reenviar/i });
    await user.click(resendBtn);

    expect(mockOnResend).toHaveBeenCalledTimes(1);
  });

  it('triggers onCancel callback when cancel request button is clicked', async () => {
    const user = userEvent.setup();
    render(
      <PendingEmailBanner
        pendingEmail="nuevo.correo@example.com"
        onResend={mockOnResend}
        onCancel={mockOnCancel}
        initialCooldownSeconds={0}
      />
    );

    const cancelBtn = screen.getByRole('button', { name: /cancelar solicitud/i });
    await user.click(cancelBtn);

    expect(mockOnCancel).toHaveBeenCalledTimes(1);
  });
});
