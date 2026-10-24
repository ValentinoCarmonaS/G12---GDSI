import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { StaffManagementPage } from '@/pages/StaffManagementPage';
import { staffApi } from '@/api/staffApi';
import * as AuthModule from '@/hooks/useAuth';
import { StaffMemberResponse } from '@/types/staff';

vi.mock('@/api/staffApi');
vi.mock('@/hooks/useAuth');

const mockStaffMembers: StaffMemberResponse[] = [
  {
    id: 'staff-1',
    fullName: 'Administrador Principal',
    email: 'admin@restaurant.com',
    role: 'ADMINISTRADOR',
    status: 'ACTIVE',
    createdAt: '2026-10-24T10:00:00Z',
    updatedAt: '2026-10-24T10:00:00Z',
  },
  {
    id: 'staff-2',
    fullName: 'Carlos Chef',
    email: 'carlos@restaurant.com',
    role: 'CHEF',
    status: 'PENDING_FIRST_LOGIN',
    createdAt: '2026-10-24T11:00:00Z',
    updatedAt: '2026-10-24T11:00:00Z',
  },
  {
    id: 'staff-3',
    fullName: 'Laura Encargada',
    email: 'laura@restaurant.com',
    role: 'ENCARGADO',
    status: 'ACTIVE',
    createdAt: '2026-10-24T12:00:00Z',
    updatedAt: '2026-10-24T12:00:00Z',
  },
];

describe('StaffManagementPage Component (FR-001, FR-004, FR-028, FR-029)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'staff-1',
        email: 'admin@restaurant.com',
        role: 'ADMINISTRADOR',
        tenantId: 'tenant-100',
        tokenVersion: 1,
        mustChangePassword: false,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('loads and displays staff members in the roster table', async () => {
    vi.mocked(staffApi.listStaff).mockResolvedValueOnce(mockStaffMembers);

    render(
      <MemoryRouter>
        <StaffManagementPage />
      </MemoryRouter>
    );

    expect(screen.getByText('Gestión del Personal')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /nómina del establecimiento/i })).toBeInTheDocument();

    expect(await screen.findByText('Administrador Principal')).toBeInTheDocument();
    expect(screen.getByText('carlos@restaurant.com')).toBeInTheDocument();
    expect(screen.getByText('Laura Encargada')).toBeInTheDocument();
    expect(screen.getByText(/pendiente de primer login/i)).toBeInTheDocument();
  });

  it('filters staff members by text search', async () => {
    const user = userEvent.setup();
    vi.mocked(staffApi.listStaff).mockResolvedValueOnce(mockStaffMembers);

    render(
      <MemoryRouter>
        <StaffManagementPage />
      </MemoryRouter>
    );

    await screen.findByText('Administrador Principal');

    const searchInput = screen.getByPlaceholderText(/buscar por nombre o email/i);
    await user.type(searchInput, 'carlos');

    expect(screen.getByText('Carlos Chef')).toBeInTheDocument();
    expect(screen.queryByText('Administrador Principal')).not.toBeInTheDocument();
    expect(screen.queryByText('Laura Encargada')).not.toBeInTheDocument();
  });

  it('registers a new staff member and opens TemporaryCredentialsModal with generated password', async () => {
    const user = userEvent.setup();
    vi.mocked(staffApi.listStaff).mockResolvedValue(mockStaffMembers);
    vi.mocked(staffApi.createStaff).mockResolvedValueOnce({
      id: 'staff-4',
      fullName: 'Nuevo Mozo',
      email: 'mozo@restaurant.com',
      role: 'ENCARGADO',
      status: 'PENDING_FIRST_LOGIN',
      temporaryCredential: 'SecureTemp99!',
      createdAt: '2026-10-24T14:00:00Z',
    });

    render(
      <MemoryRouter>
        <StaffManagementPage />
      </MemoryRouter>
    );

    await screen.findByText('Administrador Principal');

    const newButton = screen.getByRole('button', { name: /nuevo empleado/i });
    await user.click(newButton);

    expect(await screen.findByRole('heading', { name: /registrar nuevo empleado/i })).toBeInTheDocument();

    await user.type(screen.getByLabelText(/nombre completo/i), 'Nuevo Mozo');
    await user.type(screen.getByLabelText(/correo electrónico/i), 'mozo@restaurant.com');
    await user.selectOptions(screen.getByLabelText(/^rol/i), 'ENCARGADO');

    await user.click(screen.getByRole('button', { name: /registrar empleado/i }));

    await waitFor(() => {
      expect(staffApi.createStaff).toHaveBeenCalledWith({
        fullName: 'Nuevo Mozo',
        email: 'mozo@restaurant.com',
        role: 'ENCARGADO',
      });
    });

    expect(await screen.findByRole('heading', { name: /credencial temporal generada/i })).toBeInTheDocument();
    expect(screen.getByText('SecureTemp99!')).toBeInTheDocument();
  });

  it('opens edit modal and updates staff member details', async () => {
    const user = userEvent.setup();
    vi.mocked(staffApi.listStaff).mockResolvedValue(mockStaffMembers);
    vi.mocked(staffApi.updateStaff).mockResolvedValueOnce({
      id: 'staff-2',
      fullName: 'Carlos Chef Actualizado',
      email: 'carlos.nuevo@restaurant.com',
      role: 'CHEF',
      status: 'ACTIVE',
      createdAt: '2026-10-24T11:00:00Z',
      updatedAt: '2026-10-24T14:00:00Z',
    });

    render(
      <MemoryRouter>
        <StaffManagementPage />
      </MemoryRouter>
    );

    await screen.findByText('Carlos Chef');

    const editButtons = screen.getAllByRole('button', { name: /editar/i });
    await user.click(editButtons[1]); // Second row is Carlos Chef

    expect(await screen.findByRole('heading', { name: /editar miembro del personal/i })).toBeInTheDocument();

    const nameInput = screen.getByLabelText(/nombre completo/i);
    await user.clear(nameInput);
    await user.type(nameInput, 'Carlos Chef Actualizado');

    await user.click(screen.getByRole('button', { name: /guardar cambios/i }));

    await waitFor(() => {
      expect(staffApi.updateStaff).toHaveBeenCalledWith('staff-2', {
        fullName: 'Carlos Chef Actualizado',
        email: 'carlos@restaurant.com',
        role: 'CHEF',
      });
    });
  });
});
