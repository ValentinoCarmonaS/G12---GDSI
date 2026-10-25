import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppRoutes } from '@/router/AppRoutes';
import * as AuthModule from '@/hooks/useAuth';
import { authApi } from '@/api/authApi';
import { staffApi } from '@/api/staffApi';
import { inventoryApi } from '@/api/inventoryApi';

vi.mock('@/hooks/useAuth');
vi.mock('@/api/staffApi', () => ({
  staffApi: {
    listStaff: vi.fn().mockResolvedValue([]),
    createStaff: vi.fn(),
    updateStaff: vi.fn(),
  },
}));
vi.mock('@/api/inventoryApi', () => ({
  inventoryApi: {
    listIngredients: vi.fn().mockResolvedValue([]),
    createIngredient: vi.fn(),
    updateIngredient: vi.fn(),
    updateStatus: vi.fn(),
    adjustStock: vi.fn(),
    getAuditHistory: vi.fn(),
    getChefCatalog: vi.fn().mockResolvedValue({
      items: [],
      totalElements: 0,
      totalPages: 0,
      page: 0,
      size: 20,
      hasNext: false,
    }),
  },
}));

vi.mock('@/api/profileApi', () => ({
  profileApi: {
    getProfile: vi.fn().mockResolvedValue({
      id: 'diner-1',
      email: 'comensal@easyeat.com',
      firstName: 'Mateo',
      lastName: 'Gómez',
      phone: '+54 11 4444-5555',
      role: 'COMENSAL',
      emailChangePending: false,
      pendingEmail: null,
      emailVerificationLastSentAt: null,
      cooldownSecondsRemaining: 0,
    }),
    updatePersonalData: vi.fn(),
    requestEmailChange: vi.fn(),
    cancelEmailChange: vi.fn(),
    confirmEmailChange: vi.fn(),
  },
}));

describe('AppRoutes - Counter Workspace Routing (T021, ENC-1.2.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
  });

  it('renders CounterWorkspacePage when authenticated ENCARGADO visits /mostrador', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'encargado-1',
        email: 'maria.encargada@laesquina.com',
        role: 'ENCARGADO',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/mostrador']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/mostrador y despacho/i)).toBeInTheDocument();
  });

  it('instantly redirects authenticated ENCARGADO from /login to /mostrador', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'encargado-1',
        email: 'maria.encargada@laesquina.com',
        role: 'ENCARGADO',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/mostrador y despacho/i)).toBeInTheDocument();
  });

  it('redirects authenticated ENCARGADO from root / to /mostrador', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'encargado-1',
        email: 'maria.encargada@laesquina.com',
        role: 'ENCARGADO',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/mostrador y despacho/i)).toBeInTheDocument();
  });
});

describe('AppRoutes - Kitchen Workspace Routing (T031, CHF-1.2.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
  });

  it('renders KitchenWorkspacePage when authenticated CHEF visits /cocina', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-1',
        email: 'carlos.chef@laesquina.com',
        role: 'CHEF',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/cocina']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/kitchen display system/i)).toBeInTheDocument();
  });

  it('instantly redirects authenticated CHEF from /login to /cocina', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-1',
        email: 'carlos.chef@laesquina.com',
        role: 'CHEF',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/kitchen display system/i)).toBeInTheDocument();
  });

  it('redirects authenticated CHEF from root / to /cocina', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-1',
        email: 'carlos.chef@laesquina.com',
        role: 'CHEF',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/kitchen display system/i)).toBeInTheDocument();
  });

  it('redirects legacy /kds route to /cocina', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-1',
        email: 'carlos.chef@laesquina.com',
        role: 'CHEF',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/kds']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/kitchen display system/i)).toBeInTheDocument();
  });
});

describe('AppRoutes - Admin Workspace Routing (T036, ADM-1.1.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
  });

  it('renders AdminWorkspacePage when authenticated ADMINISTRADOR visits /admin', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'admin-1',
        email: 'roberto.admin@laesquina.com',
        role: 'ADMINISTRADOR',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/panel de administración/i)).toBeInTheDocument();
  });

  it('instantly redirects authenticated ADMINISTRADOR from /login to /admin', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'admin-1',
        email: 'roberto.admin@laesquina.com',
        role: 'ADMINISTRADOR',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/panel de administración/i)).toBeInTheDocument();
  });

  it('redirects authenticated ADMINISTRADOR from root / to /admin', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'admin-1',
        email: 'roberto.admin@laesquina.com',
        role: 'ADMINISTRADOR',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/panel de administración/i)).toBeInTheDocument();
  });

  it('renders ResetPasswordPage when navigating to /reset-password with token', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: false,
      user: null,
      token: null,
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/reset-password?token=sample-reset-token']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/restablecé tu contraseña/i)).toBeInTheDocument();
  });

  it('renders ResetPasswordPage when navigating to /auth/reset-password with token (email link compatibility)', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: false,
      user: null,
      token: null,
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/auth/reset-password?token=sample-reset-token']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/restablecé tu contraseña/i)).toBeInTheDocument();
  });
});

describe('AppRoutes - Feature 004 Routes & RBAC Perimeter (T074, FR-028, FR-030, FR-031, FR-032)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
    vi.mocked(staffApi.listStaff).mockResolvedValue([]);
    vi.mocked(inventoryApi.listIngredients).mockResolvedValue([]);
    vi.mocked(inventoryApi.getChefCatalog).mockResolvedValue({
      items: [],
      totalElements: 0,
      totalPages: 0,
      page: 0,
      size: 20,
      hasNext: false,
    });
  });

  describe('/first-login-password-change perimeter', () => {
    it('renders FirstLoginPasswordPage when authenticated user must change password', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'user-must-change',
          email: 'newbie@easyeat.com',
          role: 'CHEF',
          tenantId: 'tenant-123',
          tokenVersion: 0,
          mustChangePassword: true,
        },
        token: 'temp-jwt',
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/first-login-password-change']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByText(/cambio obligatorio de contraseña/i)).toBeInTheDocument();
    });

    it('redirects unauthenticated visitor from /first-login-password-change to /login', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: false,
        user: null,
        token: null,
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/first-login-password-change']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByRole('button', { name: /ingresar/i })).toBeInTheDocument();
    });

    it('redirects user with mustChangePassword=false from /first-login-password-change to role default route', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'admin-ready',
          email: 'admin@easyeat.com',
          role: 'ADMINISTRADOR',
          tenantId: 'tenant-123',
          tokenVersion: 1,
          mustChangePassword: false,
        },
        token: 'active-jwt',
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/first-login-password-change']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByText(/panel de administración/i)).toBeInTheDocument();
    });
  });

  describe('/admin/staff perimeter', () => {
    it('renders StaffManagementPage when authenticated ADMINISTRADOR visits /admin/staff', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'admin-1',
          email: 'admin@easyeat.com',
          role: 'ADMINISTRADOR',
          tenantId: 'tenant-123',
          tokenVersion: 1,
        },
        token: 'admin-jwt',
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/admin/staff']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByText(/gestión del personal/i)).toBeInTheDocument();
    });

    it('blocks non-admin (e.g. CHEF) from /admin/staff and redirects to /cocina', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'chef-1',
          email: 'chef@easyeat.com',
          role: 'CHEF',
          tenantId: 'tenant-123',
          tokenVersion: 1,
        },
        token: 'chef-jwt',
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/admin/staff']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByText(/kitchen display system/i)).toBeInTheDocument();
    });
  });

  describe('/admin/inventory perimeter', () => {
    it('renders InventoryManagementPage when authenticated ADMINISTRADOR visits /admin/inventory', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'admin-1',
          email: 'admin@easyeat.com',
          role: 'ADMINISTRADOR',
          tenantId: 'tenant-123',
          tokenVersion: 1,
        },
        token: 'admin-jwt',
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/admin/inventory']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByText(/inventario y materias primas/i)).toBeInTheDocument();
    });

    it('blocks non-admin (e.g. ENCARGADO) from /admin/inventory and redirects to /mostrador', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'encargado-1',
          email: 'encargado@easyeat.com',
          role: 'ENCARGADO',
          tenantId: 'tenant-123',
          tokenVersion: 1,
        },
        token: 'encargado-jwt',
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/admin/inventory']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByText(/mostrador y despacho/i)).toBeInTheDocument();
    });
  });

  describe('/cocina/ingredientes and /kds/ingredients perimeter', () => {
    it('renders ChefIngredientsPage when authenticated CHEF visits /cocina/ingredientes', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'chef-1',
          email: 'chef@easyeat.com',
          role: 'CHEF',
          tenantId: 'tenant-123',
          tokenVersion: 1,
        },
        token: 'chef-jwt',
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/cocina/ingredientes']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByText(/catálogo de ingredientes de cocina/i)).toBeInTheDocument();
    });

    it('redirects /kds/ingredients alias to /cocina/ingredientes for CHEF', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'chef-1',
          email: 'chef@easyeat.com',
          role: 'CHEF',
          tenantId: 'tenant-123',
          tokenVersion: 1,
        },
        token: 'chef-jwt',
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/kds/ingredients']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByText(/catálogo de ingredientes de cocina/i)).toBeInTheDocument();
    });

    it('blocks non-chef (e.g. ENCARGADO) from /cocina/ingredientes and redirects to /mostrador', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'encargado-1',
          email: 'encargado@easyeat.com',
          role: 'ENCARGADO',
          tenantId: 'tenant-123',
          tokenVersion: 1,
        },
        token: 'encargado-jwt',
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/cocina/ingredientes']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByText(/mostrador y despacho/i)).toBeInTheDocument();
    });
  });

  describe('/perfil and /verificar-email perimeters (T027, COM-1.4.1)', () => {
    it('renders ProfilePage when authenticated COMENSAL visits /perfil', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'diner-1',
          email: 'comensal@easyeat.com',
          role: 'COMENSAL',
          tokenVersion: 1,
        },
        token: 'diner-jwt',
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/perfil']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByRole('heading', { name: /mi perfil/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /guardar cambios/i })).toBeInTheDocument();
    });

    it('redirects /profile alias to /perfil for COMENSAL', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'diner-1',
          email: 'comensal@easyeat.com',
          role: 'COMENSAL',
          tokenVersion: 1,
        },
        token: 'diner-jwt',
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/profile']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByRole('heading', { name: /mi perfil/i })).toBeInTheDocument();
    });

    it('blocks non-COMENSAL (e.g. CHEF) from /perfil and redirects to /cocina', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'chef-1',
          email: 'chef@easyeat.com',
          role: 'CHEF',
          tenantId: 'tenant-123',
          tokenVersion: 1,
        },
        token: 'chef-jwt',
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/perfil']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByText(/comandas kds/i)).toBeInTheDocument();
    });

    it('redirects unauthenticated visitor from /perfil to /login', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: false,
        user: null,
        token: null,
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/perfil']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByRole('button', { name: /ingresar/i })).toBeInTheDocument();
    });

    it('renders VerifyEmailPage on public /verificar-email route', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: false,
        user: null,
        token: null,
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/verificar-email']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByText(/verificación de correo electrónico/i)).toBeInTheDocument();
    });

    it('redirects /verify-email alias to /verificar-email', async () => {
      vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
        isAuthenticated: false,
        user: null,
        token: null,
        setToken: vi.fn(),
        clearAuth: vi.fn(),
        logout: vi.fn(),
      });

      render(
        <MemoryRouter initialEntries={['/verify-email']}>
          <AppRoutes />
        </MemoryRouter>
      );

      expect(await screen.findByText(/verificación de correo electrónico/i)).toBeInTheDocument();
    });
  });
});
