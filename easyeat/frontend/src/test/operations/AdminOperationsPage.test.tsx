import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AppRoutes } from '@/router/AppRoutes';
import { AdminOperationsPage } from '@/pages/AdminOperationsPage';
import { AdminWorkspacePage } from '@/pages/AdminWorkspacePage';
import { operationsApi } from '@/api/operationsApi';
import { authApi } from '@/api/authApi';
import * as AuthModule from '@/hooks/useAuth';
import type { Role } from '@/types/auth';
import type { OperatingSchedule, SlotCapacity } from '@/types/operations';

vi.mock('@/hooks/useAuth');
vi.mock('@/api/operationsApi', () => ({
  operationsApi: {
    getSchedules: vi.fn(),
    updateSchedules: vi.fn(),
    getSlotCapacities: vi.fn(),
    updateSlotCapacities: vi.fn(),
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

const capacityMatrix: SlotCapacity[] = [
  { id: null, slotDate: '2026-11-11', startTime: '12:00:00', endTime: '12:15:00', maxCapacity: 0, approvedOrdersCount: 0, isSoldOut: true, version: null },
  { id: 'c-2', slotDate: '2026-11-11', startTime: '12:15:00', endTime: '12:30:00', maxCapacity: 20, approvedOrdersCount: 5, isSoldOut: false, version: 3 },
];

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
    vi.mocked(operationsApi.getSlotCapacities).mockResolvedValue(capacityMatrix);
  });

  it('loads the schedules through operationsApi and renders the schedule section', async () => {
    authenticateAs('ADMINISTRADOR');
    render(
      <MemoryRouter>
        <AdminOperationsPage />
      </MemoryRouter>
    );

    expect(await screen.findByText('Operación del Restaurante')).toBeInTheDocument();
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
    await userEvent.click(screen.getByRole('button', { name: /guardar horarios/i }));

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

  it('US2: renders the capacity section and loads the matrix of the chosen date', async () => {
    authenticateAs('ADMINISTRADOR');
    render(
      <MemoryRouter>
        <AdminOperationsPage />
      </MemoryRouter>
    );

    expect(await screen.findByRole('heading', { name: /capacidad por franja/i })).toBeInTheDocument();
    expect(await screen.findByLabelText('Capacidad 12:15')).toHaveValue(20);
    const today = vi.mocked(operationsApi.getSlotCapacities).mock.calls[0][0];
    expect(today).toMatch(/^\d{4}-\d{2}-\d{2}$/);

    fireEvent.change(screen.getByLabelText('Fecha'), { target: { value: '2026-11-11' } });
    await waitFor(() => expect(operationsApi.getSlotCapacities).toHaveBeenCalledWith('2026-11-11'));
  });

  it('US2: saves the capacity list in one call and reloads the matrix', async () => {
    authenticateAs('ADMINISTRADOR');
    vi.mocked(operationsApi.updateSlotCapacities).mockResolvedValue(capacityMatrix);
    render(
      <MemoryRouter>
        <AdminOperationsPage />
      </MemoryRouter>
    );

    fireEvent.change(await screen.findByLabelText('Capacidad para todas las franjas'), { target: { value: '30' } });
    await userEvent.click(screen.getByRole('button', { name: /aplicar a todas/i }));
    await userEvent.click(screen.getByRole('button', { name: /guardar capacidad/i }));

    await waitFor(() => expect(operationsApi.updateSlotCapacities).toHaveBeenCalledTimes(1));
    const sent = vi.mocked(operationsApi.updateSlotCapacities).mock.calls[0][0];
    expect(sent).toHaveLength(2);
    expect(sent.map((change) => change.maxCapacity)).toEqual([30, 30]);
    await waitFor(() => expect(operationsApi.getSlotCapacities).toHaveBeenCalledTimes(2));
  });

  it('US2: shows a load error for the capacity matrix', async () => {
    authenticateAs('ADMINISTRADOR');
    vi.mocked(operationsApi.getSlotCapacities).mockRejectedValue(new Error('boom'));
    render(
      <MemoryRouter>
        <AdminOperationsPage />
      </MemoryRouter>
    );

    expect(await screen.findByText('No se pudo cargar la capacidad por franja. Intente nuevamente.')).toBeInTheDocument();
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
    vi.mocked(operationsApi.getSlotCapacities).mockResolvedValue(capacityMatrix);
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

  it.each<Role>(['CHEF', 'ENCARGADO'])('does not render the page for %s', async (role) => {
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
