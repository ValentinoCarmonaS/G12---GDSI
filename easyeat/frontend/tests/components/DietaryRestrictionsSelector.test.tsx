import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DietaryRestrictionsSelector } from '@/components/profile/DietaryRestrictionsSelector';
import type { DietaryRestrictionItem } from '@/types/profile';

describe('DietaryRestrictionsSelector Component (T031, COM-1.4.3)', () => {
  const mockOnSave = vi.fn();

  const sampleTaxonomy: DietaryRestrictionItem[] = [
    {
      code: 'CELIAC',
      name: 'Sin TACC / Celíaco',
      category: 'DIETARY_LIFESTYLE',
      description: 'Excluye trigo, avena, cebada y centeno.',
    },
    {
      code: 'VEGAN',
      name: 'Vegano',
      category: 'DIETARY_LIFESTYLE',
      description: 'Excluye productos animales.',
    },
    {
      code: 'VEGETARIAN',
      name: 'Vegetariano',
      category: 'DIETARY_LIFESTYLE',
      description: 'Excluye carnes y pescados.',
    },
    {
      code: 'DAIRY',
      name: 'Lácteos / Lactosa',
      category: 'COMMON_ALLERGEN',
      description: 'Presencia de leche o derivados.',
    },
    {
      code: 'PEANUTS',
      name: 'Maní',
      category: 'COMMON_ALLERGEN',
      description: 'Presencia de maní o derivados.',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders taxonomy grouped into Lifestyles and Common Allergens (FR-012)', () => {
    render(
      <DietaryRestrictionsSelector
        availableTaxonomy={sampleTaxonomy}
        selectedCodes={['CELIAC']}
        onSave={mockOnSave}
      />
    );

    expect(screen.getByText(/estilos de vida/i)).toBeInTheDocument();
    expect(screen.getByText(/alérgenos comunes/i)).toBeInTheDocument();

    expect(screen.getByText('Sin TACC / Celíaco')).toBeInTheDocument();
    expect(screen.getByText('Vegano')).toBeInTheDocument();
    expect(screen.getByText('Lácteos / Lactosa')).toBeInTheDocument();
    expect(screen.getByText('Maní')).toBeInTheDocument();
  });

  it('reflects initial active selections correctly', () => {
    render(
      <DietaryRestrictionsSelector
        availableTaxonomy={sampleTaxonomy}
        selectedCodes={['CELIAC', 'PEANUTS']}
        onSave={mockOnSave}
      />
    );

    const celiacCheckbox = screen.getByLabelText(/sin tacc \/ celíaco/i);
    const veganCheckbox = screen.getByLabelText(/vegano/i);
    const peanutsCheckbox = screen.getByLabelText(/maní/i);

    expect(celiacCheckbox).toBeChecked();
    expect(veganCheckbox).not.toBeChecked();
    expect(peanutsCheckbox).toBeChecked();
  });

  it('allows multi-selection toggles and submits updated codes on save', async () => {
    const user = userEvent.setup();
    mockOnSave.mockResolvedValueOnce(undefined);

    render(
      <DietaryRestrictionsSelector
        availableTaxonomy={sampleTaxonomy}
        selectedCodes={['CELIAC']}
        onSave={mockOnSave}
      />
    );

    const veganCheckbox = screen.getByLabelText(/vegano/i);
    const celiacCheckbox = screen.getByLabelText(/sin tacc \/ celíaco/i);

    // Toggle VEGAN on
    await user.click(veganCheckbox);
    expect(veganCheckbox).toBeChecked();

    // Toggle CELIAC off
    await user.click(celiacCheckbox);
    expect(celiacCheckbox).not.toBeChecked();

    const saveBtn = screen.getByRole('button', { name: /guardar preferencias/i });
    await user.click(saveBtn);

    expect(mockOnSave).toHaveBeenCalledTimes(1);
    expect(mockOnSave).toHaveBeenCalledWith(['VEGAN']);
  });

  it('displays explicit error alert on save failure with 0% optimistic state (FR-014)', async () => {
    const user = userEvent.setup();
    mockOnSave.mockRejectedValueOnce(new Error('Fallo de conexión'));

    render(
      <DietaryRestrictionsSelector
        availableTaxonomy={sampleTaxonomy}
        selectedCodes={['CELIAC']}
        onSave={mockOnSave}
      />
    );

    const veganCheckbox = screen.getByLabelText(/vegano/i);
    await user.click(veganCheckbox);

    const saveBtn = screen.getByRole('button', { name: /guardar preferencias/i });
    await user.click(saveBtn);

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/no se pudieron guardar las preferencias|fallo de conexión/i)).toBeInTheDocument();
  });

  it('renders advisory notification regarding active kitchen orders (FR-016)', () => {
    render(
      <DietaryRestrictionsSelector
        availableTaxonomy={sampleTaxonomy}
        selectedCodes={[]}
        onSave={mockOnSave}
      />
    );

    expect(
      screen.getByText(/pedidos en preparación|cocina|contactá al restaurante/i)
    ).toBeInTheDocument();
  });
});
