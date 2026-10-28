import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { OperatingHoursBanner } from '@/components/catalog/OperatingHoursBanner';

describe('OperatingHoursBanner Component Tests (T035, US2, COM-4.1.1, FR-012)', () => {
  it('does not render when isOpen is true', () => {
    const { container } = render(<OperatingHoursBanner isOpen={true} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders read-only banner and schedule when isOpen is false', () => {
    render(<OperatingHoursBanner isOpen={false} nextOpeningSchedule="Abre hoy a las 19:30" />);

    expect(screen.getByTestId('operating-hours-banner')).toBeInTheDocument();
    expect(screen.getByText('Establecimiento fuera de horario de atención')).toBeInTheDocument();
    expect(screen.getByText(/Abre hoy a las 19:30/)).toBeInTheDocument();
    expect(screen.getByText('Modo Lectura')).toBeInTheDocument();
  });
});
