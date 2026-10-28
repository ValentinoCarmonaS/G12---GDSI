import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { EstablishmentBlockingNotice } from '@/components/catalog/EstablishmentBlockingNotice';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('EstablishmentBlockingNotice Component Tests (T021, COM-1.5.1 CA 4, FR-005)', () => {
  it('renders blocking message and establishment name', () => {
    render(
      <MemoryRouter>
        <EstablishmentBlockingNotice
          establishmentName="Bistró San Telmo"
          status="PAUSED"
        />
      </MemoryRouter>
    );

    expect(screen.getByText('Establecimiento inactivo')).toBeInTheDocument();
    expect(screen.getByText('Bistró San Telmo')).toBeInTheDocument();
    expect(
      screen.getByText(/este establecimiento se encuentra temporalmente inactivo/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/estado: paused/i)).toBeInTheDocument();
  });

  it('navigates to /descubrimiento when clicking return button', () => {
    render(
      <MemoryRouter>
        <EstablishmentBlockingNotice establishmentName="Bistró San Telmo" />
      </MemoryRouter>
    );

    const backButton = screen.getByRole('button', {
      name: /volver a buscar restaurantes/i,
    });
    fireEvent.click(backButton);

    expect(mockNavigate).toHaveBeenCalledWith('/descubrimiento');
  });

  it('calls custom onBackToSearch callback if provided', () => {
    const onBackMock = vi.fn();
    render(
      <MemoryRouter>
        <EstablishmentBlockingNotice
          establishmentName="Bistró San Telmo"
          onBackToSearch={onBackMock}
        />
      </MemoryRouter>
    );

    const backButton = screen.getByRole('button', {
      name: /volver a buscar restaurantes/i,
    });
    fireEvent.click(backButton);

    expect(onBackMock).toHaveBeenCalledTimes(1);
  });
});
