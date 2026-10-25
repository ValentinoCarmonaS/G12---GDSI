import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppRoutes } from '../../src/router/AppRoutes';
import * as AuthModule from '../../src/hooks/useAuth';

vi.mock('../../src/hooks/useAuth');
vi.mock('../../src/api/profileApi', () => ({
  profileApi: {
    getProfile: vi.fn().mockResolvedValue({
      id: 'diner-1',
      email: 'comensal@easyeat.com',
      firstName: 'Valentino',
      lastName: 'Carmona',
      phone: '+54 11 1234-5678',
      role: 'COMENSAL',
      emailChangePending: false,
      pendingEmail: null,
      emailVerificationLastSentAt: null,
      cooldownSecondsRemaining: 0,
      activeDietaryRestrictions: [],
    }),
    updatePersonalData: vi.fn(),
    getDietaryTaxonomy: vi.fn().mockResolvedValue([]),
    updateDietaryPreferences: vi.fn(),
  },
}));

describe('AppRoutes - Profile & Catalog Routing Integration (T046, US3, FR-026)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('allows unauthenticated guest access to /catalogo with login and register action links (FR-026)', () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: false,
      user: null,
      token: null,
      login: vi.fn(),
      logout: vi.fn(),
    } as any);

    render(
      <MemoryRouter initialEntries={['/catalogo']}>
        <AppRoutes />
      </MemoryRouter>
    );

    // Verifies CatalogPage is displayed and header invites to log in/register
    expect(screen.getByText(/Catálogo de Productos/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Iniciar sesión/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Registrarse/i })).toBeInTheDocument();
  });

  it('allows unauthenticated guest access to establishment catalog /catalogo/:tenantId', () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: false,
      user: null,
      token: null,
      login: vi.fn(),
      logout: vi.fn(),
    } as any);

    render(
      <MemoryRouter initialEntries={['/catalogo/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(screen.getByText(/Catálogo de Productos/i)).toBeInTheDocument();
  });

  it('redirects canonical alias /catalog to /catalogo', () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: false,
      user: null,
      token: null,
      login: vi.fn(),
      logout: vi.fn(),
    } as any);

    render(
      <MemoryRouter initialEntries={['/catalog']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(screen.getByText(/Catálogo de Productos/i)).toBeInTheDocument();
  });

  it('intercepts unauthenticated access to /perfil and redirects to /login', () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: false,
      user: null,
      token: null,
      login: vi.fn(),
      logout: vi.fn(),
    } as any);

    render(
      <MemoryRouter initialEntries={['/perfil']}>
        <AppRoutes />
      </MemoryRouter>
    );

    // Unauthenticated user attempting /perfil must be intercepted by RoleRouteGuard to /login
    expect(screen.queryByRole('heading', { name: /Mi perfil/i })).toBeNull();
  });

  it('renders ProfilePage when user is authenticated with COMENSAL role', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'diner-1',
        email: 'comensal@easyeat.com',
        role: 'COMENSAL',
        status: 'ACTIVE',
        mustChangePassword: false,
      },
      token: 'jwt-token',
      login: vi.fn(),
      logout: vi.fn(),
    } as any);

    render(
      <MemoryRouter initialEntries={['/perfil']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByRole('heading', { name: /Mi perfil/i })).toBeInTheDocument();
  });

  it('redirects non-COMENSAL staff attempting to access /perfil to their default workspace', () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-1',
        email: 'chef@easyeat.com',
        role: 'CHEF',
        status: 'ACTIVE',
        mustChangePassword: false,
      },
      token: 'jwt-token',
      login: vi.fn(),
      logout: vi.fn(),
    } as any);

    render(
      <MemoryRouter initialEntries={['/perfil']}>
        <AppRoutes />
      </MemoryRouter>
    );

    // RoleRouteGuard redirects CHEF to /cocina
    expect(screen.queryByRole('heading', { name: /Mi perfil/i })).toBeNull();
    expect(screen.getByText(/Kitchen Display System/i)).toBeInTheDocument();
  });

  it('redirects compatibility alias /profile to /perfil for authenticated COMENSAL', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'diner-1',
        email: 'comensal@easyeat.com',
        role: 'COMENSAL',
        status: 'ACTIVE',
        mustChangePassword: false,
      },
      token: 'jwt-token',
      login: vi.fn(),
      logout: vi.fn(),
    } as any);

    render(
      <MemoryRouter initialEntries={['/profile']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByRole('heading', { name: /Mi perfil/i })).toBeInTheDocument();
  });
});
