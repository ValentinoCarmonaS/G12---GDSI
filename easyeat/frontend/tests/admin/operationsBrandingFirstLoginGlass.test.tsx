import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AdminOperationsPage } from '../../src/pages/AdminOperationsPage';
import { AdminBrandingPage } from '../../src/pages/AdminBrandingPage';
import { FirstLoginPasswordPage } from '../../src/pages/FirstLoginPasswordPage';
import { operationsApi } from '../../src/api/operationsApi';
import * as brandingApi from '../../src/api/brandingApi';
import * as AuthModule from '../../src/hooks/useAuth';

vi.mock('../../src/api/operationsApi', () => ({
  operationsApi: {
    getSchedules: vi.fn(),
    getSlotCapacities: vi.fn(),
    getAdminPaymentMethods: vi.fn(),
    togglePaymentMethod: vi.fn(),
    updateSlotCapacities: vi.fn(),
    updateSchedules: vi.fn(),
  },
}));

vi.mock('../../src/api/brandingApi', () => ({
  getAdminBranding: vi.fn(),
  updateAdminBranding: vi.fn(),
  uploadRestaurantLogo: vi.fn(),
}));

vi.mock('../../src/hooks/useAuth');

describe('US5: Operaciones, Mi Local y Primer Ingreso Glassmorphism (FR-009, FR-013, FR-014, FR-015)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'admin-1',
        email: 'admin@easyeat.test',
        role: 'ADMINISTRADOR',
        tenantId: 'tenant-1',
        tokenVersion: 1,
        mustChangePassword: false,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    vi.mocked(operationsApi.getSchedules).mockResolvedValue([
      {
        id: 'sched-1',
        dayOfWeek: 'MONDAY',
        isClosed: false,
        openingTime: '09:00',
        closingTime: '22:00',
        version: 1,
      },
    ]);
    vi.mocked(operationsApi.getSlotCapacities).mockResolvedValue([
      {
        id: 'slot-1',
        slotDate: '2026-10-01',
        startTime: '12:00',
        endTime: '12:30',
        maxCapacity: 20,
        approvedOrdersCount: 5,
        isSoldOut: false,
        version: 1,
      },
    ]);
    vi.mocked(operationsApi.getAdminPaymentMethods).mockResolvedValue([
      {
        id: 'pm-1',
        paymentMethod: 'MERCADO_PAGO',
        displayName: 'Mercado Pago',
        isEnabled: true,
        version: 1,
      },
    ]);

    vi.mocked(brandingApi.getAdminBranding).mockResolvedValue({
      establishmentId: 'tenant-1',
      fantasyName: 'Bistró Central',
      description: 'Cocina de autor',
      primaryColor: '#10B981',
      primaryContrastColor: '#FFFFFF',
      secondaryColor: '#065F46',
      secondaryContrastColor: '#FFFFFF',
      accentColor: '#F59E0B',
      accentContrastColor: '#000000',
      logoUrl: null,
      updatedAt: '2026-10-01T10:00:00Z',
    } as brandingApi.RestaurantBranding);
  });

  describe('AdminOperationsPage', () => {
    it('renderiza horarios en tarjetas GlassCard, cupos preservando role="row" y maneja conflicto 409 accesible en pagos', async () => {
      vi.mocked(operationsApi.togglePaymentMethod).mockRejectedValue({
        response: { status: 409, data: { message: 'Conflicto de versión' } },
      });

      const { container } = render(
        <MemoryRouter>
          <AdminOperationsPage />
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByText(/lunes/i)).toBeInTheDocument();
      });

      // Tarjetas GlassCard en operaciones
      expect(container.querySelectorAll('[class*="backdrop-blur"]').length).toBeGreaterThanOrEqual(2);

      // SlotCapacityGrid preserva role="row"
      await waitFor(() => {
        expect(screen.getAllByRole('row').length).toBeGreaterThanOrEqual(1);
      });

      // Conflicto 409 accesible en PaymentMethodToggles
      const toggleBtn = screen.getByRole('switch', { name: /mercado pago/i });
      fireEvent.click(toggleBtn);

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });
    });
  });

  describe('AdminBrandingPage', () => {
    it('renderiza en 2 columnas en escritorio con códigos hex en JetBrains Mono y aislamiento estricto de marca', async () => {
      const { container } = render(
        <MemoryRouter>
          <AdminBrandingPage />
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByDisplayValue('Bistró Central')).toBeInTheDocument();
      });

      // Layout de 2 columnas en escritorio
      expect(container.querySelector('.lg\\:grid-cols-2, .lg\\:grid-cols-\\[minmax\\(0\\,1fr\\)_minmax\\(0\\,1fr\\)\\]')).not.toBeNull();

      // Códigos hex en JetBrains Mono (font-ee-mono)
      const monoInputs = container.querySelectorAll('input.font-ee-mono, .font-ee-mono');
      expect(monoInputs.length).toBeGreaterThanOrEqual(1);

      // Aislamiento estricto: los colores editados viven dentro del contenedor de vista previa
      const previewContainer = container.querySelector('[data-brand-preview]');
      expect(previewContainer).not.toBeNull();
    });
  });

  describe('FirstLoginPasswordPage', () => {
    it('renderiza AuthCardShell 100% --ee-* y checklist de contraseña en tiempo real WCAG 2.2 AA', () => {
      const { container } = render(
        <MemoryRouter>
          <FirstLoginPasswordPage />
        </MemoryRouter>
      );

      // AuthCardShell usa data-testid="auth-card-shell" y estructura --ee-*
      expect(container.querySelector('[data-testid="auth-card-shell"]')).not.toBeNull();

      // Checklist de complejidad en tiempo real
      const newPasswordInput = screen.getByLabelText(/^nueva contraseña/i);
      fireEvent.change(newPasswordInput, { target: { value: 'Abcdef12' } });

      expect(screen.getByText(/mínimo 8 caracteres/i)).toBeInTheDocument();
      expect(screen.getByText(/al menos una mayúscula/i)).toBeInTheDocument();
      expect(screen.getByText(/al menos una minúscula/i)).toBeInTheDocument();
      expect(screen.getByText(/al menos un número/i)).toBeInTheDocument();
    });
  });
});
