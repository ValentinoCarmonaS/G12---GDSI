import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BrandThemeProvider } from '../../components/common/BrandThemeProvider';
import * as brandingApi from '../../api/brandingApi';

vi.mock('../../api/brandingApi', () => ({
  getDinerBranding: vi.fn(),
}));

describe('T045 [US3]: DinerCatalogBranding Tests - Dynamic CSS Variables and Neutral Fallback (INV-009, INV-010, CB-010, CB-011)', () => {
  const customBranding = {
    establishmentId: 'est-custom',
    fantasyName: 'Pizzería Napolitana',
    description: 'Auténtica pizza al horno de leña',
    logoUrl: '/uploads/logos/est-custom/logo.png',
    primaryColor: '#DC2626',
    secondaryColor: '#991B1B',
    accentColor: '#F59E0B',
    primaryContrastColor: '#FFFFFF',
    secondaryContrastColor: '#FFFFFF',
    accentContrastColor: '#000000',
    updatedAt: '2026-11-10T12:00:00Z',
    isDefaultFallback: false,
  };

  const neutralFallbackBranding = {
    establishmentId: 'est-fallback',
    fantasyName: 'Restaurante EasyEat',
    description: 'Restaurante asociado a EasyEat',
    logoUrl: '/assets/brand/easyeat-logo-neutral.svg',
    primaryColor: '#10B981',
    secondaryColor: '#065F46',
    accentColor: '#F59E0B',
    primaryContrastColor: '#FFFFFF',
    secondaryContrastColor: '#FFFFFF',
    accentContrastColor: '#000000',
    updatedAt: '2026-11-10T12:00:00Z',
    isDefaultFallback: true,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('injects custom CSS branding variables into container when establishment has custom branding (INV-009, CB-010)', async () => {
    vi.mocked(brandingApi.getDinerBranding).mockResolvedValueOnce(customBranding);

    render(
      <BrandThemeProvider establishmentId="est-custom">
        <div data-testid="themed-catalog-view">
          <button data-testid="branded-cta">Pedir ahora</button>
        </div>
      </BrandThemeProvider>
    );

    await waitFor(() => {
      const container = screen.getByTestId('brand-theme-container');
      expect(container.style.getPropertyValue('--brand-primary')).toBe('#DC2626');
      expect(container.style.getPropertyValue('--brand-primary-contrast')).toBe('#FFFFFF');
      expect(container.style.getPropertyValue('--brand-secondary')).toBe('#991B1B');
      expect(container.style.getPropertyValue('--brand-accent')).toBe('#F59E0B');
      expect(container.style.getPropertyValue('--brand-accent-contrast')).toBe('#000000');
    });
  });

  it('injects neutral EasyEat default CSS variables when branding not configured (INV-010, CB-011)', async () => {
    vi.mocked(brandingApi.getDinerBranding).mockResolvedValueOnce(neutralFallbackBranding);

    render(
      <BrandThemeProvider establishmentId="est-fallback">
        <div data-testid="themed-catalog-view">
          <button data-testid="branded-cta">Pedir ahora</button>
        </div>
      </BrandThemeProvider>
    );

    await waitFor(() => {
      const container = screen.getByTestId('brand-theme-container');
      expect(container.style.getPropertyValue('--brand-primary')).toBe('#10B981');
      expect(container.style.getPropertyValue('--brand-primary-contrast')).toBe('#FFFFFF');
      expect(container.style.getPropertyValue('--brand-secondary')).toBe('#065F46');
    });
  });

  it('safely applies neutral fallback if API call fails without throwing runtime errors', async () => {
    vi.mocked(brandingApi.getDinerBranding).mockRejectedValueOnce(new Error('Network error'));

    render(
      <BrandThemeProvider establishmentId="est-error">
        <div data-testid="themed-catalog-view">Contenido seguro</div>
      </BrandThemeProvider>
    );

    await waitFor(() => {
      const container = screen.getByTestId('brand-theme-container');
      expect(container.style.getPropertyValue('--brand-primary')).toBe('#10B981');
      expect(screen.getByText('Contenido seguro')).toBeInTheDocument();
    });
  });
});
