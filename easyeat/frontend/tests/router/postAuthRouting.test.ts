import { describe, it, expect } from 'vitest';
import {
  getRoleDefaultRoute,
  isRouteAllowedForRole,
  resolvePostAuthRedirect,
} from '@/router/postAuthRouting';
import { Role } from '@/types/auth';

describe('postAuthRouting Policy', () => {
  describe('getRoleDefaultRoute (FR-007, FR-017)', () => {
    it('maps ENCARGADO to /counter', () => {
      expect(getRoleDefaultRoute('ENCARGADO')).toBe('/counter');
    });

    it('maps CHEF to /kds', () => {
      expect(getRoleDefaultRoute('CHEF')).toBe('/kds');
    });

    it('maps ADMINISTRADOR to /admin', () => {
      expect(getRoleDefaultRoute('ADMINISTRADOR')).toBe('/admin');
    });

    it('maps COMENSAL to /catalog', () => {
      expect(getRoleDefaultRoute('COMENSAL')).toBe('/catalog');
    });

    it('falls back safely to /catalog if role is undefined or unrecognized (FR-017)', () => {
      expect(getRoleDefaultRoute(undefined as unknown as Role)).toBe('/catalog');
      expect(getRoleDefaultRoute('UNKNOWN_ROLE' as unknown as Role)).toBe('/catalog');
    });
  });

  describe('isRouteAllowedForRole', () => {
    it('allows ENCARGADO on counter paths only', () => {
      expect(isRouteAllowedForRole('ENCARGADO', '/counter')).toBe(true);
      expect(isRouteAllowedForRole('ENCARGADO', '/counter/orders/123')).toBe(true);
      expect(isRouteAllowedForRole('ENCARGADO', '/kds')).toBe(false);
      expect(isRouteAllowedForRole('ENCARGADO', '/admin')).toBe(false);
    });

    it('allows CHEF on kds paths only', () => {
      expect(isRouteAllowedForRole('CHEF', '/kds')).toBe(true);
      expect(isRouteAllowedForRole('CHEF', '/kds/station-2')).toBe(true);
      expect(isRouteAllowedForRole('CHEF', '/counter')).toBe(false);
      expect(isRouteAllowedForRole('CHEF', '/admin')).toBe(false);
    });

    it('allows ADMINISTRADOR on admin paths', () => {
      expect(isRouteAllowedForRole('ADMINISTRADOR', '/admin')).toBe(true);
      expect(isRouteAllowedForRole('ADMINISTRADOR', '/admin/settings')).toBe(true);
      expect(isRouteAllowedForRole('ADMINISTRADOR', '/kds')).toBe(false);
    });

    it('allows COMENSAL on catalog paths', () => {
      expect(isRouteAllowedForRole('COMENSAL', '/catalog')).toBe(true);
      expect(isRouteAllowedForRole('COMENSAL', '/counter')).toBe(false);
    });
  });

  describe('resolvePostAuthRedirect (FR-008, FR-009)', () => {
    it('returns role default route when no target URL is provided', () => {
      expect(resolvePostAuthRedirect('CHEF')).toBe('/kds');
      expect(resolvePostAuthRedirect('ENCARGADO', undefined)).toBe('/counter');
      expect(resolvePostAuthRedirect('ADMINISTRADOR', '/')).toBe('/admin');
      expect(resolvePostAuthRedirect('COMENSAL', '/login')).toBe('/catalog');
    });

    it('preserves target deep link if it matches user role (FR-009)', () => {
      expect(resolvePostAuthRedirect('CHEF', '/kds/station-main')).toBe('/kds/station-main');
      expect(resolvePostAuthRedirect('ENCARGADO', '/counter/dispatch/4')).toBe('/counter/dispatch/4');
      expect(resolvePostAuthRedirect('ADMINISTRADOR', '/admin/reports')).toBe('/admin/reports');
    });

    it('discards unauthorized target deep link and routes to user default workspace (FR-009, SC-003)', () => {
      // CHEF attempting to access /admin/reports must land on /kds
      expect(resolvePostAuthRedirect('CHEF', '/admin/reports')).toBe('/kds');

      // ENCARGADO attempting to access /kds must land on /counter
      expect(resolvePostAuthRedirect('ENCARGADO', '/kds/station-1')).toBe('/counter');

      // COMENSAL attempting to access /counter must land on /catalog
      expect(resolvePostAuthRedirect('COMENSAL', '/counter')).toBe('/catalog');
    });
  });
});
