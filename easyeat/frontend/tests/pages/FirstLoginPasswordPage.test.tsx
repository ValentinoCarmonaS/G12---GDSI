import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { FirstLoginPasswordPage } from '@/pages/FirstLoginPasswordPage';
import { authApi } from '@/api/authApi';
import * as AuthModule from '@/hooks/useAuth';

vi.mock('@/api/authApi');
vi.mock('@/hooks/useAuth');

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('FirstLoginPasswordPage Component (FR-003, FR-030)', () => {
  const mockSetToken = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'user-pending-1',
        email: 'staff@restaurant.com',
        role: 'ADMINISTRADOR',
        tenantId: 'tenant-100',
        tokenVersion: 0,
        mustChangePassword: true,
      },
      token: 'mock-old-token',
      setToken: mockSetToken,
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('renders all required password inputs and submit button', () => {
    render(
      <MemoryRouter>
        <FirstLoginPasswordPage />
      </MemoryRouter>
    );

    expect(screen.getByLabelText(/contraseña actual/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^nueva contraseña/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirmar nueva contraseña/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cambiar contraseña y continuar/i })).toBeInTheDocument();
  });

  it('displays validation error when required fields are empty', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <FirstLoginPasswordPage />
      </MemoryRouter>
    );

    await user.click(screen.getByRole('button', { name: /cambiar contraseña y continuar/i }));

    expect(await screen.findByText(/la contraseña actual es obligatoria/i)).toBeInTheDocument();
  });

  it('displays error when passwords do not match', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <FirstLoginPasswordPage />
      </MemoryRouter>
    );

    await user.type(screen.getByLabelText(/contraseña actual/i), 'TempPass123!');
    await user.type(screen.getByLabelText(/^nueva contraseña/i), 'ValidPass123!');
    await user.type(screen.getByLabelText(/confirmar nueva contraseña/i), 'DifferentPass123!');

    await user.click(screen.getByRole('button', { name: /cambiar contraseña y continuar/i }));

    expect(await screen.findByText(/las contraseñas no coinciden/i)).toBeInTheDocument();
    expect(authApi.changeFirstLoginPassword).not.toHaveBeenCalled();
  });

  it('displays error when new password does not meet complexity requirements', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <FirstLoginPasswordPage />
      </MemoryRouter>
    );

    await user.type(screen.getByLabelText(/contraseña actual/i), 'TempPass123!');
    await user.type(screen.getByLabelText(/^nueva contraseña/i), 'weak');
    await user.type(screen.getByLabelText(/confirmar nueva contraseña/i), 'weak');

    await user.click(screen.getByRole('button', { name: /cambiar contraseña y continuar/i }));

    expect(await screen.findByText(/la contraseña no cumple con los requisitos mínimos de seguridad/i)).toBeInTheDocument();
    expect(authApi.changeFirstLoginPassword).not.toHaveBeenCalled();
  });

  it('submits valid password change, updates token, and navigates to default workspace', async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.changeFirstLoginPassword).mockResolvedValueOnce({
      access_token: 'new-active-jwt-token',
      token_type: 'Bearer',
      expires_in: 28800,
      user_id: 'user-pending-1',
      status: 'ACTIVE',
    });

    render(
      <MemoryRouter>
        <FirstLoginPasswordPage />
      </MemoryRouter>
    );

    await user.type(screen.getByLabelText(/contraseña actual/i), 'TempPass123!');
    await user.type(screen.getByLabelText(/^nueva contraseña/i), 'StrongNewPass123!');
    await user.type(screen.getByLabelText(/confirmar nueva contraseña/i), 'StrongNewPass123!');

    await user.click(screen.getByRole('button', { name: /cambiar contraseña y continuar/i }));

    await waitFor(() => {
      expect(authApi.changeFirstLoginPassword).toHaveBeenCalledWith({
        currentPassword: 'TempPass123!',
        newPassword: 'StrongNewPass123!',
        confirmNewPassword: 'StrongNewPass123!',
      });
    });

    expect(mockSetToken).toHaveBeenCalledWith('new-active-jwt-token');
    expect(mockNavigate).toHaveBeenCalledWith('/admin', { replace: true });
  });

  it('displays server error message when current password is wrong (HTTP 400/401)', async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.changeFirstLoginPassword).mockRejectedValueOnce({
      response: {
        status: 400,
        data: { detail: 'La contraseña actual no es correcta' },
      },
    });

    render(
      <MemoryRouter>
        <FirstLoginPasswordPage />
      </MemoryRouter>
    );

    await user.type(screen.getByLabelText(/contraseña actual/i), 'WrongTempPass123!');
    await user.type(screen.getByLabelText(/^nueva contraseña/i), 'StrongNewPass123!');
    await user.type(screen.getByLabelText(/confirmar nueva contraseña/i), 'StrongNewPass123!');

    await user.click(screen.getByRole('button', { name: /cambiar contraseña y continuar/i }));

    expect(await screen.findByText(/la contraseña actual no es correcta/i)).toBeInTheDocument();
  });
});
