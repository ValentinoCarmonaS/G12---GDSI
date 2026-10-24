import { describe, it, expect } from 'vitest';
import {
  getRoleDefaultRoute,
  isRouteAllowedForRole,
  resolvePostAuthRedirect,
} from '@/router/postAuthRouting';
import { Role } from '@/types/auth';

describe('postAuthRouting Policy (Canonical Spanish Routes)', () => {
  describe('getRoleDefaultRoute (FR-005, FR-006)', () => {
    it('maps ENCARGADO to /mostrador', () => {
      expect(getRoleDefaultRoute('ENCARGADO')).toBe('/mostrador');
    });

    it('maps CHEF to /cocina', () => {
      expect(getRoleDefaultRoute('CHEF')).toBe('/cocina');
    });

    it('maps ADMINISTRADOR to /admin', () => {
      expect(getRoleDefaultRoute('ADMINISTRADOR')).toBe('/admin');
    });

    it('maps COMENSAL to /catalogo', () => {
      expect(getRoleDefaultRoute('COMENSAL')).toBe('/catalogo');
    });

    it('falls back safely to /catalogo if role is undefined or unrecognized', () => {
      expect(getRoleDefaultRoute(undefined as unknown as Role)).toBe('/catalogo');
      expect(getRoleDefaultRoute('UNKNOWN_ROLE' as unknown as Role)).toBe('/catalogo');
    });
  });

  describe('isRouteAllowedForRole', () => {
    it('allows ENCARGADO on /mostrador paths only', () => {
      expect(isRouteAllowedForRole('ENCARGADO', '/mostrador')).toBe(true);
      expect(isRouteAllowedForRole('ENCARGADO', '/mostrador/pedidos/123')).toBe(true);
      expect(isRouteAllowedForRole('ENCARGADO', '/cocina')).toBe(false);
      expect(isRouteAllowedForRole('ENCARGADO', '/admin')).toBe(false);
    });

    it('allows CHEF on /cocina paths only', () => {
      expect(isRouteAllowedForRole('CHEF', '/cocina')).toBe(true);
      expect(isRouteAllowedForRole('CHEF', '/cocina/estacion-2')).toBe(true);
      expect(isRouteAllowedForRole('CHEF', '/mostrador')).toBe(false);
      expect(isRouteAllowedForRole('CHEF', '/admin')).toBe(false);
    });

    it('allows ADMINISTRADOR on /admin paths', () => {
      expect(isRouteAllowedForRole('ADMINISTRADOR', '/admin')).toBe(true);
      expect(isRouteAllowedForRole('ADMINISTRADOR', '/admin/configuracion')).toBe(true);
      expect(isRouteAllowedForRole('ADMINISTRADOR', '/cocina')).toBe(false);
    });

    it('allows COMENSAL on /catalogo paths', () => {
      expect(isRouteAllowedForRole('COMENSAL', '/catalogo')).toBe(true);
      expect(isRouteAllowedForRole('COMENSAL', '/mostrador')).toBe(false);
    });
  });

  describe('resolvePostAuthRedirect (FR-005, FR-006)', () => {
    it('returns role default route when no target URL is provided', () => {
      expect(resolvePostAuthRedirect('CHEF')).toBe('/cocina');
      expect(resolvePostAuthRedirect('ENCARGADO', undefined)).toBe('/mostrador');
      expect(resolvePostAuthRedirect('ADMINISTRADOR', '/')).toBe('/admin');
      expect(resolvePostAuthRedirect('COMENSAL', '/login')).toBe('/catalogo');
    });

    it('preserves target deep link if it matches user role (FR-006)', () => {
      expect(resolvePostAuthRedirect('CHEF', '/cocina/estacion-principal')).toBe('/cocina/estacion-principal');
      expect(resolvePostAuthRedirect('ENCARGADO', '/mostrador/despacho/4')).toBe('/mostrador/despacho/4');
      expect(resolvePostAuthRedirect('ADMINISTRADOR', '/admin/reportes')).toBe('/admin/reportes');
    });

    it('discards unauthorized target deep link and routes to user default workspace (FR-006, SC-003)', () => {
      // CHEF attempting to access /admin/reportes must land on /cocina
      expect(resolvePostAuthRedirect('CHEF', '/admin/reportes')).toBe('/cocina');

      // ENCARGADO attempting to access /cocina/estacion-1 must land on /mostrador
      expect(resolvePostAuthRedirect('ENCARGADO', '/cocina/estacion-1')).toBe('/mostrador');

      // COMENSAL attempting to access /mostrador must land on /catalogo
      expect(resolvePostAuthRedirect('COMENSAL', '/mostrador')).toBe('/catalogo');
    });
  });

  describe('User Story 2: Redirection Policy for ENCARGADO (T020, ENC-1.2.1)', () => {
    it('resolves directly to /mostrador when no target link is specified', () => {
      expect(resolvePostAuthRedirect('ENCARGADO')).toBe('/mostrador');
      expect(resolvePostAuthRedirect('ENCARGADO', '')).toBe('/mostrador');
    });

    it('preserves authorized deep link targets within /mostrador hierarchy', () => {
      expect(resolvePostAuthRedirect('ENCARGADO', '/mostrador/entregas')).toBe('/mostrador/entregas');
      expect(resolvePostAuthRedirect('ENCARGADO', '/mostrador/caja')).toBe('/mostrador/caja');
      expect(resolvePostAuthRedirect('ENCARGADO', '/mostrador/pedidos/turno-tarde')).toBe('/mostrador/pedidos/turno-tarde');
    });

    it('discards unauthorized targets outside /mostrador and redirects to /mostrador', () => {
      expect(resolvePostAuthRedirect('ENCARGADO', '/cocina')).toBe('/mostrador');
      expect(resolvePostAuthRedirect('ENCARGADO', '/cocina/pedidos')).toBe('/mostrador');
      expect(resolvePostAuthRedirect('ENCARGADO', '/admin')).toBe('/mostrador');
      expect(resolvePostAuthRedirect('ENCARGADO', '/admin/usuarios')).toBe('/mostrador');
      expect(resolvePostAuthRedirect('ENCARGADO', '/catalogo')).toBe('/mostrador');
    });
  });

  describe('User Story 4: Redirection Policy for CHEF (T028, CHF-1.2.1)', () => {
    it('resolves directly to /cocina when no target link is specified or target is empty', () => {
      expect(resolvePostAuthRedirect('CHEF')).toBe('/cocina');
      expect(resolvePostAuthRedirect('CHEF', '')).toBe('/cocina');
      expect(resolvePostAuthRedirect('CHEF', undefined)).toBe('/cocina');
    });

    it('preserves authorized deep link targets within /cocina station paths', () => {
      expect(resolvePostAuthRedirect('CHEF', '/cocina/estacion-1')).toBe('/cocina/estacion-1');
      expect(resolvePostAuthRedirect('CHEF', '/cocina/pedidos/urgentes')).toBe('/cocina/pedidos/urgentes');
      expect(resolvePostAuthRedirect('CHEF', '/cocina/kds-turno-noche')).toBe('/cocina/kds-turno-noche');
    });

    it('discards unauthorized targets outside /cocina and redirects to default /cocina', () => {
      expect(resolvePostAuthRedirect('CHEF', '/mostrador')).toBe('/cocina');
      expect(resolvePostAuthRedirect('CHEF', '/mostrador/entregas')).toBe('/cocina');
      expect(resolvePostAuthRedirect('CHEF', '/admin')).toBe('/cocina');
      expect(resolvePostAuthRedirect('CHEF', '/admin/reportes')).toBe('/cocina');
      expect(resolvePostAuthRedirect('CHEF', '/catalogo')).toBe('/cocina');
    });
  });
});


