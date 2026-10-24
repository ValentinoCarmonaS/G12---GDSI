import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import { TemporaryCredentialsModal } from '@/components/staff/TemporaryCredentialsModal';

describe('TemporaryCredentialsModal Component (FR-003, FR-029)', () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    staffName: 'Carlos Chef',
    email: 'carlos@restaurant.com',
    temporaryCredential: 'TempPass1234!',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('does not render modal when isOpen is false', () => {
    render(<TemporaryCredentialsModal {...defaultProps} isOpen={false} />);
    expect(screen.queryByText(/credencial temporal generada/i)).not.toBeInTheDocument();
  });

  it('renders staff name, email, and temporary credential when isOpen is true', () => {
    render(<TemporaryCredentialsModal {...defaultProps} />);

    expect(screen.getByText(/credencial temporal generada/i)).toBeInTheDocument();
    expect(screen.getByText('Carlos Chef')).toBeInTheDocument();
    expect(screen.getByText('carlos@restaurant.com')).toBeInTheDocument();
    expect(screen.getByText('TempPass1234!')).toBeInTheDocument();
  });

  it('renders a prominent security warning indicating credential is shown only once', () => {
    render(<TemporaryCredentialsModal {...defaultProps} />);

    expect(
      screen.getByText(/esta contraseña solo se mostrará una vez/i)
    ).toBeInTheDocument();
  });

  it('copies credential to clipboard on copy button click', async () => {
    const user = userEvent.setup();
    const writeTextMock = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, {
      clipboard: {
        writeText: writeTextMock,
      },
    });

    render(<TemporaryCredentialsModal {...defaultProps} />);

    const copyButton = screen.getByRole('button', { name: /copiar/i });
    await user.click(copyButton);

    expect(writeTextMock).toHaveBeenCalledWith('TempPass1234!');
    expect(await screen.findByText(/copiado/i)).toBeInTheDocument();
  });

  it('calls onClose when close button is clicked', async () => {
    const user = userEvent.setup();
    render(<TemporaryCredentialsModal {...defaultProps} />);

    const closeButton = screen.getByRole('button', { name: /entendido/i });
    await user.click(closeButton);

    expect(defaultProps.onClose).toHaveBeenCalledTimes(1);
  });
});
