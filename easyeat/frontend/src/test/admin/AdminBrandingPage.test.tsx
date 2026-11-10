import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AdminBrandingPage } from '../../pages/AdminBrandingPage';
import * as brandingApi from '../../api/brandingApi';

vi.mock('../../api/brandingApi', () => ({
  getAdminBranding: vi.fn(),
  updateAdminBranding: vi.fn(),
  uploadRestaurantLogo: vi.fn(),
}));

vi.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({
    user: {
      id: 'admin-123',
      email: 'admin@restaurant.com',
      role: 'ADMINISTRADOR',
      tenantId: 'est-123',
    },
    logout: vi.fn(),
  }),
}));

vi.mock('../../api/profileApi', () => ({
  profileApi: {
    getProfile: vi.fn().mockResolvedValue({ avatarUrl: null }),
  },
}));

vi.mock('../../api/adminNotificationApi', () => ({
  adminNotificationApi: {
    getUnreadCount: vi.fn().mockResolvedValue(0),
    getNotifications: vi.fn().mockResolvedValue([]),
  },
}));

describe('T044 [US3]: AdminBrandingPage Component Tests (AS-9, INV-009, CB-009)', () => {
  const mockInitialBranding = {
    establishmentId: 'est-123',
    fantasyName: 'Café Central Palermo',
    description: 'Cafetería de especialidad',
    logoUrl: '/uploads/logos/est-123/logo.png',
    primaryColor: '#10B981',
    secondaryColor: '#065F46',
    accentColor: '#F59E0B',
    primaryContrastColor: '#FFFFFF',
    secondaryContrastColor: '#FFFFFF',
    accentContrastColor: '#000000',
    updatedAt: '2026-11-10T12:00:00Z',
    isDefaultFallback: false,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderComponent = () => {
    return render(
      <MemoryRouter>
        <AdminBrandingPage />
      </MemoryRouter>
    );
  };

  it('loads and displays current establishment branding with triad preview', async () => {
    vi.mocked(brandingApi.getAdminBranding).mockResolvedValueOnce(mockInitialBranding);

    renderComponent();

    await waitFor(() => {
      expect(screen.getByDisplayValue('Café Central Palermo')).toBeInTheDocument();
      expect(screen.getByDisplayValue('Cafetería de especialidad')).toBeInTheDocument();
      expect(screen.getByDisplayValue('#10B981')).toBeInTheDocument();
      expect(screen.getByDisplayValue('#065F46')).toBeInTheDocument();
      expect(screen.getByDisplayValue('#F59E0B')).toBeInTheDocument();
    });

    expect(screen.getByText(/previsualización de marca/i)).toBeInTheDocument();
  });

  it('updates branding form and calls updateAdminBranding on submit', async () => {
    vi.mocked(brandingApi.getAdminBranding).mockResolvedValueOnce(mockInitialBranding);
    vi.mocked(brandingApi.updateAdminBranding).mockResolvedValueOnce({
      ...mockInitialBranding,
      fantasyName: 'Nuevo Nombre Gourmet',
      primaryColor: '#2563EB',
      primaryContrastColor: '#FFFFFF',
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByDisplayValue('Café Central Palermo')).toBeInTheDocument();
    });

    const nameInput = screen.getByDisplayValue('Café Central Palermo');
    fireEvent.change(nameInput, { target: { value: 'Nuevo Nombre Gourmet' } });

    const primaryColorInput = screen.getByDisplayValue('#10B981');
    fireEvent.change(primaryColorInput, { target: { value: '#2563EB' } });

    const saveButton = screen.getByRole('button', { name: /guardar/i });
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(brandingApi.updateAdminBranding).toHaveBeenCalledWith(
        'est-123',
        expect.objectContaining({
          fantasyName: 'Nuevo Nombre Gourmet',
          primaryColor: '#2563EB',
        })
      );
      expect(screen.getByText(/guardado exitosamente|branding actualizado/i)).toBeInTheDocument();
    });
  });

  it('handles logo file upload and updates displayed logo preview', async () => {
    vi.mocked(brandingApi.getAdminBranding).mockResolvedValueOnce(mockInitialBranding);
    vi.mocked(brandingApi.uploadRestaurantLogo).mockResolvedValueOnce({
      logoUrl: '/uploads/logos/est-123/nuevo-logo.png',
      updatedAt: '2026-11-10T14:00:00Z',
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByDisplayValue('Café Central Palermo')).toBeInTheDocument();
    });

    const file = new File(['dummy-content'], 'nuevo-logo.png', { type: 'image/png' });
    const fileInput = screen.getByTestId('logo-file-input');
    fireEvent.change(fileInput, { target: { files: [file] } });

    await waitFor(() => {
      expect(brandingApi.uploadRestaurantLogo).toHaveBeenCalledWith('est-123', file);
    });
  });
});
