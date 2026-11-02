import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AppRoutes } from '@/router/AppRoutes';
import { AdminOperationsPage } from '@/pages/AdminOperationsPage';
import { AdminWorkspacePage } from '@/pages/AdminWorkspacePage';
import { operationsApi } from '@/api/operationsApi';
import { authApi } from '@/api/authApi';
import * as AuthModule from '@/hooks/useAuth';
import type { Role } from '@/types/auth';
import type { OperatingSchedule } from '@/types/operations';

vi.mock('@/hooks/useAuth');
vi.mock('@/api/operationsApi', () => ({
  operationsApi: {
    getSchedules: vi.fn(),
    updateSchedules: vi.fn(),
  },
}));
vi.mock('@/api/staffApi', () => ({
  staffApi: { listStaff: vi.fn().mockResolvedValue([]), createStaff: vi.fn(), updateStaff: vi.fn() },
}));
vi.mock('@/api/inventoryApi', () => ({
  inventoryApi: { listIngredients: vi.fn().mockResolvedValue([]) },
}));
vi.mock('@/api/dishReviewApi', () => ({
  dishReviewApi: {
    getInbox: vi.fn().mockResolvedValue({
      items: [], page: 0, size: 1, totalElements: 0, totalPages: 0, hasNext: false, pendingCount: 0,
    }),
  },
}));
vi.mock('@/api/adminNotificationApi', () => ({
  adminNotificationApi: {
    listNotifications: vi.fn().mockResolvedValue({
      content: [], pageNumber: 0, pageSize: 20, totalElements: 0, totalPages: 0, isLast: true, unreadCount: 0,
    }),
    markAsRead: vi.fn(),
  },
}));

const monday: OperatingSchedule = {
  id: 'sch-1',
  dayOfWeek: 'MONDAY',
  openingTime: '11:00:00',
  closingTime: '16:00:00',
  isClosed: false,
  version: 0,
};

const HUB_SUBTITLE = 'Configuración global del establecimiento, usuarios y auditoría';

function authenticateAs(role: Role) {
  vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
    isAuthenticated: true,
    user: { id: 'u-1', email: 'u@easyeat.com', role, tenantId: 'tenant-123', tokenVersion: 1 },
    token: 'jwt',
    setToken: vi.fn(),
    clearAuth: vi.fn(),
    logout: vi.fn(),
  });
}

describe('AdminOperationsPage (US1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
    vi.mocked(operationsApi.getSchedules).mockResolvedValue([monday]);
  });

  it('loads the schedules through operationsApi and renders the schedule section', async () => {
    authenticateAs('ADMINISTRADOR');
    render(
      <MemoryRouter>
        <AdminOperationsPage />
      </MemoryRouter>
    );

    expect(await screen.findByRole('heading', { name: /operación del restaurante/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /horarios comerciales/i })).toBeInTheDocument();
    expect(await screen.findByLabelText('Apertura Lunes')).toHaveValue('11:00');
    expect(operationsApi.getSchedules).toHaveBeenCalledTimes(1);
  });

  it('saves the changes and reloads the week', async () => {
    authenticateAs('ADMINISTRADOR');
    vi.mocked(operationsApi.updateSchedules).mockResolvedValue([{ ...monday, isClosed: true, version: 1 }]);
    render(
      <MemoryRouter>
        <AdminOperationsPage />
      </MemoryRouter>
    );

    await userEvent.click(await screen.findByLabelText('Cerrado Lunes'));
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    await waitFor(() =>
      expect(operationsApi.updateSchedules).toHaveBeenCalledWith([
        { dayOfWeek: 'MONDAY', openingTime: '11:00:00', closingTime: '16:00:00', isClosed: true, expectedVersion: 0 },
      ])
    );
    await waitFor(() => expect(operationsApi.getSchedules).toHaveBeenCalledTimes(2));
  });

  it('shows a load error when the schedules cannot be fetched', async () => {
    authenticateAs('ADMINISTRADOR');
    vi.mocked(operationsApi.getSchedules).mockRejectedValue(new Error('boom'));
    render(
      <MemoryRouter>
        <AdminOperationsPage />
      </MemoryRouter>
    );

    expect(await screen.findByText('No se pudieron cargar los horarios. Intente nuevamente.')).toBeInTheDocument();
  });

  it('is linked from the administration hub', async () => {
    authenticateAs('ADMINISTRADOR');
    render(
      <MemoryRouter>
        <AdminWorkspacePage />
      </MemoryRouter>
    );

    const link = await screen.findByRole('link', { name: /operación del restaurante/i });
    expect(link).toHaveAttribute('href', '/admin/operations');
  });
});

describe('route /admin/operations (guard and order)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
    vi.mocked(operationsApi.getSchedules).mockResolvedValue([monday]);
  });

  it('renders the page for an ADMINISTRADOR, not the /admin/* hub that follows it', async () => {
    authenticateAs('ADMINISTRADOR');
    render(
      <MemoryRouter initialEntries={['/admin/operations']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByRole('heading', { name: /horarios comerciales/i })).toBeInTheDocument();
    expect(screen.queryByText(HUB_SUBTITLE)).not.toBeInTheDocument();
  });

  it.each<Role>(['COMENSAL', 'CHEF', 'ENCARGADO'])('does not render the page for %s', async (role) => {
    authenticateAs(role);
    render(
      <MemoryRouter initialEntries={['/admin/operations']}>
        <AppRoutes />
      </MemoryRouter>
    );

    await waitFor(() => expect(screen.queryByRole('heading', { name: /horarios comerciales/i })).not.toBeInTheDocument());
    expect(operationsApi.getSchedules).not.toHaveBeenCalled();
  });
});
