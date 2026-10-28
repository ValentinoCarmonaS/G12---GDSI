import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AllergenStep } from '@/components/dish/AllergenStep';

describe('AllergenStep Component (T050, CHF-2.2.3, FR-013, FR-014, FR-015)', () => {
  const mockOnSaveAllergens = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders both taxonomy groups: Dietary Lifestyles and Common Allergens with appropriate indicators', () => {
    render(
      <AllergenStep
        dishId="d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"
        initialRestrictions={[]}
        initialConfirmed={false}
        onSaveAllergens={mockOnSaveAllergens}
      />
    );

    // Group headings
    expect(screen.getByText(/Estilos de Vida Dietarios/i)).toBeInTheDocument();
    expect(screen.getByText(/Alérgenos Comunes/i)).toBeInTheDocument();

    // Dietary lifestyle items
    expect(screen.getByText('Vegano')).toBeInTheDocument();
    expect(screen.getByText('Vegetariano')).toBeInTheDocument();
    expect(screen.getByText('Celíaco')).toBeInTheDocument();

    // Common allergens items
    expect(screen.getByText('Lácteos')).toBeInTheDocument();
    expect(screen.getByText('Maní')).toBeInTheDocument();
    expect(screen.getByText('Huevos')).toBeInTheDocument();
  });

  it('allows multi-selection of lifestyles and allergens, reflecting selection state', async () => {
    const user = userEvent.setup();
    render(
      <AllergenStep
        dishId="d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"
        initialRestrictions={['VEGAN']}
        initialConfirmed={false}
        onSaveAllergens={mockOnSaveAllergens}
      />
    );

    // VEGAN should be initially selected
    const veganCheckbox = screen.getByRole('checkbox', { name: /Vegano/i });
    expect(veganCheckbox).toBeChecked();

    // Toggle DAIRY
    const dairyCheckbox = screen.getByRole('checkbox', { name: /Lácteos/i });
    expect(dairyCheckbox).not.toBeChecked();
    await user.click(dairyCheckbox);
    expect(dairyCheckbox).toBeChecked();

    // Toggle VEGAN off
    await user.click(veganCheckbox);
    expect(veganCheckbox).not.toBeChecked();
  });

  it('supports confirming the allergen section explicitly with zero labels selected', async () => {
    const user = userEvent.setup();
    render(
      <AllergenStep
        dishId="d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"
        initialRestrictions={[]}
        initialConfirmed={false}
        onSaveAllergens={mockOnSaveAllergens}
      />
    );

    // Confirm section toggle
    const confirmToggle = screen.getByRole('checkbox', { name: /Confirmar sección de alérgenos/i });
    expect(confirmToggle).not.toBeChecked();
    await user.click(confirmToggle);
    expect(confirmToggle).toBeChecked();

    // Click save
    const saveButton = screen.getByRole('button', { name: /Guardar y Confirmar Alérgenos/i });
    await user.click(saveButton);

    expect(mockOnSaveAllergens).toHaveBeenCalledWith([], true);
  });

  it('submits updated restriction codes and confirmed flag on save', async () => {
    const user = userEvent.setup();
    render(
      <AllergenStep
        dishId="d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"
        initialRestrictions={['CELIAC']}
        initialConfirmed={true}
        onSaveAllergens={mockOnSaveAllergens}
      />
    );

    const celiacCheckbox = screen.getByRole('checkbox', { name: /Celíaco/i });
    expect(celiacCheckbox).toBeChecked();

    const peanutsCheckbox = screen.getByRole('checkbox', { name: /Maní/i });
    await user.click(peanutsCheckbox);
    expect(peanutsCheckbox).toBeChecked();

    const saveButton = screen.getByRole('button', { name: /Guardar y Confirmar Alérgenos/i });
    await user.click(saveButton);

    expect(mockOnSaveAllergens).toHaveBeenCalledWith(
      expect.arrayContaining(['CELIAC', 'PEANUTS']),
      true
    );
  });

  it('disables all checkboxes and buttons when isReadOnly is true', () => {
    render(
      <AllergenStep
        dishId="d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"
        initialRestrictions={['VEGAN']}
        initialConfirmed={true}
        onSaveAllergens={mockOnSaveAllergens}
        isReadOnly={true}
      />
    );

    const checkboxes = screen.getAllByRole('checkbox');
    checkboxes.forEach((cb) => {
      expect(cb).toBeDisabled();
    });

    const saveButton = screen.getByRole('button', { name: /Guardar y Confirmar Alérgenos/i });
    expect(saveButton).toBeDisabled();
  });
});
