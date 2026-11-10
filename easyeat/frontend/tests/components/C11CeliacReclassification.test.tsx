import { describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AllergenStep } from '@/components/dish/AllergenStep';
import { RejectionDialog } from '@/components/dishreview/RejectionDialog';
import { DietaryRestrictionsSelector } from '@/components/profile/DietaryRestrictionsSelector';
import { DishDetailModal } from '@/components/catalog/DishDetailModal';
import { COMMON_ALLERGENS, DIETARY_LIFESTYLES } from '@/constants/allergenTaxonomy';
import type { DietaryRestrictionItem } from '@/types/profile';
import type { DishCatalogItem } from '@/types/dinerCatalog';

describe('C-11: Reclasificación de Celíaco de estilo de vida a alérgeno (Chef / Admin / Comensal)', () => {
  it('Taxonomy constant: CELIAC is in COMMON_ALLERGENS and NOT in DIETARY_LIFESTYLES', () => {
    const lifestyleCodes = DIETARY_LIFESTYLES.map((item) => item.code);
    const allergenCodes = COMMON_ALLERGENS.map((item) => item.code);

    expect(lifestyleCodes).toEqual(['VEGAN', 'VEGETARIAN']);
    expect(lifestyleCodes).not.toContain('CELIAC');
    expect(allergenCodes).toContain('CELIAC');
  });

  it('Chef Form (Paso 3): renders Celíaco in Alérgenos Comunes and NOT in Estilos de Vida', () => {
    render(
      <AllergenStep
        dishId="test-dish-1"
        initialRestrictions={[]}
        initialConfirmed={false}
        onSaveAllergens={vi.fn()}
      />
    );

    // Group 1: Estilos de Vida Dietarios
    const lifestylesHeader = screen.getByText(/Estilos de Vida Dietarios/i);
    const lifestylesSection = lifestylesHeader.closest('.rounded-xl') as HTMLElement;
    expect(within(lifestylesSection).queryByText('Celíaco')).not.toBeInTheDocument();
    expect(within(lifestylesSection).getByText('Vegano')).toBeInTheDocument();
    expect(within(lifestylesSection).getByText('Vegetariano')).toBeInTheDocument();

    // Group 2: Alérgenos Comunes
    const allergensHeader = screen.getByText(/Alérgenos Comunes/i);
    const allergensSection = allergensHeader.closest('.rounded-xl') as HTMLElement;
    expect(within(allergensSection).getByText('Celíaco')).toBeInTheDocument();
    expect(within(allergensSection).getByText('Lácteos')).toBeInTheDocument();
  });

  it('Admin View (RejectionDialog): groups Celíaco in Alérgenos fieldset and NOT in Estilos de vida', async () => {
    const user = userEvent.setup();
    render(
      <RejectionDialog
        isOpen={true}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    // Toggle editable proposal
    const proposalCheckbox = screen.getByLabelText(/incluir propuesta de cambios/i);
    await user.click(proposalCheckbox);

    // Find fieldsets
    const allergenLegend = screen.getByText(/^Alérgenos$/i);
    const allergenFieldset = allergenLegend.closest('fieldset') as HTMLElement;
    expect(within(allergenFieldset).getByText('Celíaco')).toBeInTheDocument();

    const lifestyleLegend = screen.getByText(/^Estilos de vida$/i);
    const lifestyleFieldset = lifestyleLegend.closest('fieldset') as HTMLElement;
    expect(within(lifestyleFieldset).queryByText('Celíaco')).not.toBeInTheDocument();
    expect(within(lifestyleFieldset).getByText('Vegano')).toBeInTheDocument();
  });

  it('Diner Profile (DietaryRestrictionsSelector): renders Sin TACC / Celíaco under Alérgenos comunes', () => {
    const taxonomy: DietaryRestrictionItem[] = [
      { code: 'CELIAC', name: 'Sin TACC / Celíaco', category: 'COMMON_ALLERGEN' },
      { code: 'VEGAN', name: 'Vegano', category: 'DIETARY_LIFESTYLE' },
      { code: 'DAIRY', name: 'Lácteos / Lactosa', category: 'COMMON_ALLERGEN' },
    ];

    render(
      <DietaryRestrictionsSelector
        availableTaxonomy={taxonomy}
        selectedCodes={['CELIAC']}
        onSave={vi.fn()}
      />
    );

    const lifestylesHeader = screen.getByText(/^Estilos de vida$/i);
    const lifestylesSection = lifestylesHeader.closest('div')!.parentElement as HTMLElement;
    expect(within(lifestylesSection).queryByText('Sin TACC / Celíaco')).not.toBeInTheDocument();

    const allergensHeader = screen.getByText(/^Alérgenos comunes$/i);
    const allergensSection = allergensHeader.closest('div')!.parentElement as HTMLElement;
    expect(within(allergensSection).getByText('Sin TACC / Celíaco')).toBeInTheDocument();
  });

  it('Diner Modal (DishDetailModal): displays Sin TACC / Celíaco as neutral allergen badge', () => {
    const dish: DishCatalogItem = {
      id: 'dish-1',
      name: 'Ñoquis de Papa Sin TACC',
      description: 'Ñoquis artesanales de papa.',
      category: 'Platos principales',
      price: 5200,
      photoUrl: null,
      isAvailable: true,
      availableStock: 10,
      isCompliant: true,
      conflictingRestrictions: [],
      recipeIngredients: ['Papa', 'Fécula de maíz'],
      allergens: ['Sin TACC / Celíaco'],
    };

    render(<DishDetailModal dish={dish} isOpen={true} onClose={vi.fn()} />);

    expect(screen.getByText(/Alérgenos/i)).toBeInTheDocument();
    const pill = screen.getByTestId('allergen-neutral-pill');
    expect(pill).toHaveTextContent('Sin TACC / Celíaco');
  });
});
