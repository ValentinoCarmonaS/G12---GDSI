import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { VerifyEmailPage } from '@/pages/VerifyEmailPage';
import { profileApi } from '@/api/profileApi';

describe('VerifyEmailPage (T024, COM-1.4.1)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('displays error when token parameter is missing from URL', async () => {
    render(
      <MemoryRouter initialEntries={['/verificar-email']}>
        <VerifyEmailPage />
      </MemoryRouter>
    );

    expect(await screen.findByText(/no se encontró ningún token/i)).toBeInTheDocument();
  });

  it('validates token, displays success message and active email when token is valid', async () => {
    vi.spyOn(profileApi, 'confirmEmailChange').mockResolvedValueOnce({
      message: 'Email verificado con éxito',
      activeEmail: 'nuevo.valen@easyeat.com',
    });

    render(
      <MemoryRouter initialEntries={['/verificar-email?token=valid-secret-token']}>
        <VerifyEmailPage />
      </MemoryRouter>
    );

    expect(await screen.findByText(/¡correo verificado con éxito!/i)).toBeInTheDocument();
    expect(screen.getByText('nuevo.valen@easyeat.com')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /iniciar sesión/i })).toHaveAttribute('href', '/login');
  });

  it('displays expired/invalid error message when token validation fails', async () => {
    vi.spyOn(profileApi, 'confirmEmailChange').mockRejectedValueOnce({
      response: {
        data: {
          detail: 'El token de verificación ha expirado.',
        },
      },
    });

    render(
      <MemoryRouter initialEntries={['/verificar-email?token=expired-token']}>
        <VerifyEmailPage />
      </MemoryRouter>
    );

    expect(await screen.findByText(/enlace no válido o expirado/i)).toBeInTheDocument();
    expect(screen.getByText(/el token de verificación ha expirado/i)).toBeInTheDocument();
  });
});
