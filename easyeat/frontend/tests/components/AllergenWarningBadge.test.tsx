import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AllergenWarningBadge } from '../../src/components/catalog/AllergenWarningBadge';

describe('AllergenWarningBadge Component (T044, US3, FR-020, FR-022)', () => {
  it('renders null when conflicts array is empty or undefined', () => {
    const { container: c1 } = render(<AllergenWarningBadge conflicts={[]} />);
    expect(c1.firstChild).toBeNull();

    const { container: c2 } = render(<AllergenWarningBadge conflicts={undefined as any} />);
    expect(c2.firstChild).toBeNull();
  });

  it('renders specific allergen warning badge with human-readable label (FR-020)', () => {
    render(<AllergenWarningBadge conflicts={['PEANUTS']} />);

    expect(screen.getByTestId('warning-badge-peanuts')).toBeInTheDocument();
    expect(screen.getByText(/Contiene Maní/i)).toBeInTheDocument();
  });

  it('renders dietary lifestyle conflict label for VEGAN and CELIAC', () => {
    render(<AllergenWarningBadge conflicts={['VEGAN', 'CELIAC']} />);

    expect(screen.getByTestId('warning-badge-vegan')).toBeInTheDocument();
    expect(screen.getByText(/No apto Vegano/i)).toBeInTheDocument();

    expect(screen.getByTestId('warning-badge-celiac')).toBeInTheDocument();
    expect(screen.getByText(/No apto Celíaco/i)).toBeInTheDocument();
  });

  it('renders multiple badges in container for complex dish conflicts', () => {
    render(<AllergenWarningBadge conflicts={['DAIRY', 'EGGS', 'WHEAT']} size="sm" />);

    const container = screen.getByTestId('allergen-warning-badge-container');
    expect(container).toBeInTheDocument();

    expect(screen.getByText(/Contiene Lácteos/i)).toBeInTheDocument();
    expect(screen.getByText(/Contiene Huevos/i)).toBeInTheDocument();
    expect(screen.getByText(/Contiene Trigo/i)).toBeInTheDocument();
  });

  it('renders fallback label when restriction code is not in predefined taxonomy map', () => {
    render(<AllergenWarningBadge conflicts={['UNKNOWN_POLLEN']} />);

    expect(screen.getByText(/Conflicto: UNKNOWN_POLLEN/i)).toBeInTheDocument();
  });
});
