import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ProfilePage } from '@/pages/ProfilePage';
import { profileApi } from '@/api/profileApi';
import * as useCartModule from '@/hooks/useCart';
import { AuthProvider } from '@/context/AuthContext';
import type { ProfileResponse, DietaryRestrictionItem } from '@/types/profile';

describe('ProfilePage (T025, T038, COM-1.4.1 & COM-1.4.3)', () => {
  const mockProfile: ProfileResponse = {
    id: 'user-uuid-1',
    firstName: 'Valentino',
    lastName: 'Carmona',
    fullName: 'Valentino Carmona',
    phone: '+5491123456789',
    email: 'valentino@example.com',
    pendingEmail: null,
    emailVerificationPending: false,
    dietaryRestrictions: [{ code: 'CELIAC', name: 'Sin TACC / Celíaco', category: 'DIETARY_LIFESTYLE' }],
  };

  const mockTaxonomy: DietaryRestrictionItem[] = [
    { code: 'CELIAC', name: 'Sin TACC / Celíaco', category: 'DIETARY_LIFESTYLE' },
    { code: 'VEGAN', name: 'Vegano', category: 'DIETARY_LIFESTYLE' },
    { code: 'PEANUTS', name: 'Maní', category: 'COMMON_ALLERGEN' },
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(profileApi, 'getDietaryTaxonomy').mockResolvedValue(mockTaxonomy);
  });

  const renderWithProviders = (ui: React.ReactElement) => {
    return render(
      <MemoryRouter>
        <AuthProvider>{ui}</AuthProvider>
      </MemoryRouter>
    );
  };

  it('renders profile page with personal data form populated', async () => {
    vi.spyOn(profileApi, 'getProfile').mockResolvedValueOnce(mockProfile);

    renderWithProviders(<ProfilePage />);

    expect(await screen.findByRole('heading', { name: 'Mi perfil' })).toBeInTheDocument();
    expect(screen.getByLabelText(/nombre/i)).toHaveValue('Valentino');
    expect(screen.getByLabelText(/apellido/i)).toHaveValue('Carmona');
    expect(screen.getByDisplayValue('valentino@example.com')).toBeInTheDocument();
  });

  it('displays PendingEmailBanner when user has pending email change', async () => {
    vi.spyOn(profileApi, 'getProfile').mockResolvedValueOnce({
      ...mockProfile,
      pendingEmail: 'pendiente@example.com',
      emailVerificationPending: true,
    });

    renderWithProviders(<ProfilePage />);

    expect(await screen.findByText(/pendiente@example.com/i)).toBeInTheDocument();
    expect(screen.getByText(/verificación pendiente/i)).toBeInTheDocument();
  });

  it('updates personal data and displays success notification', async () => {
    const user = userEvent.setup();
    vi.spyOn(profileApi, 'getProfile').mockResolvedValueOnce(mockProfile);
    const updatedProfile: ProfileResponse = {
      ...mockProfile,
      firstName: 'Agustin',
      fullName: 'Agustin Carmona',
    };
    vi.spyOn(profileApi, 'updatePersonalData').mockResolvedValueOnce(updatedProfile);

    renderWithProviders(<ProfilePage />);

    const firstNameInput = await screen.findByLabelText(/nombre/i);
    await user.clear(firstNameInput);
    await user.type(firstNameInput, 'Agustin');

    const saveBtn = screen.getByRole('button', { name: /guardar cambios/i });
    await user.click(saveBtn);

    expect(await screen.findByText(/actualizado correctamente/i)).toBeInTheDocument();
  });

  it('renders DietaryRestrictionsSelector and updates dietary preferences (T038)', async () => {
    const user = userEvent.setup();
    vi.spyOn(profileApi, 'getProfile').mockResolvedValueOnce(mockProfile);
    const updateSpy = vi.spyOn(profileApi, 'updateDietaryRestrictions').mockResolvedValueOnce([
      { code: 'CELIAC', name: 'Sin TACC / Celíaco', category: 'DIETARY_LIFESTYLE' },
      { code: 'VEGAN', name: 'Vegano', category: 'DIETARY_LIFESTYLE' },
    ]);

    renderWithProviders(<ProfilePage />);

    expect(await screen.findByText(/restricciones alimentarias y preferencias/i)).toBeInTheDocument();
    const veganCheckbox = screen.getByLabelText(/vegano/i);
    await user.click(veganCheckbox);

    const saveDietaryBtn = screen.getByRole('button', { name: /guardar preferencias/i });
    await user.click(saveDietaryBtn);

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith(['CELIAC', 'VEGAN']);
    });
    expect(await screen.findByText(/tus preferencias dietarias se han actualizado correctamente/i)).toBeInTheDocument();
  });

  it('triggers cart reEvaluateCompliance when cart has active items upon dietary save (FR-015, T038)', async () => {
    const user = userEvent.setup();
    const mockReEvaluate = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(useCartModule, 'useCart').mockReturnValue({
      items: [{ dishId: 'dish-1', name: 'Plato Test', price: 1000, quantity: 1 }],
      reEvaluateCompliance: mockReEvaluate,
    });

    vi.spyOn(profileApi, 'getProfile').mockResolvedValueOnce(mockProfile);
    vi.spyOn(profileApi, 'updateDietaryRestrictions').mockResolvedValueOnce([
      { code: 'CELIAC', name: 'Sin TACC / Celíaco', category: 'DIETARY_LIFESTYLE' },
    ]);

    renderWithProviders(<ProfilePage />);

    await screen.findByText(/restricciones alimentarias y preferencias/i);
    const saveDietaryBtn = screen.getByRole('button', { name: /guardar preferencias/i });
    await user.click(saveDietaryBtn);

    await waitFor(() => {
      expect(mockReEvaluate).toHaveBeenCalledTimes(1);
    });
  });
});
