import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import React from 'react';
import { TenantProvider, TENANT_STORAGE_KEY } from '@/context/TenantContext';
import { useTenant } from '@/hooks/useTenant';
import { mockEstablishmentSummaryActive } from '@/test/mocks/dinerCatalogHandlers';

describe('useTenant and TenantContext (T018, US1, COM-1.5.1 CA 2)', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('throws an error if used outside of TenantProvider', () => {
    expect(() => renderHook(() => useTenant())).toThrow(
      'useTenant must be used within a TenantProvider'
    );
  });

  it('initializes with null when sessionStorage is empty', () => {
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <TenantProvider>{children}</TenantProvider>
    );

    const { result } = renderHook(() => useTenant(), { wrapper });

    expect(result.current.tenant).toBeNull();
  });

  it('initializes with stored tenant from sessionStorage', () => {
    sessionStorage.setItem(
      TENANT_STORAGE_KEY,
      JSON.stringify(mockEstablishmentSummaryActive)
    );

    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <TenantProvider>{children}</TenantProvider>
    );

    const { result } = renderHook(() => useTenant(), { wrapper });

    expect(result.current.tenant).toEqual(mockEstablishmentSummaryActive);
  });

  it('updates tenant and persists to sessionStorage', () => {
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <TenantProvider>{children}</TenantProvider>
    );

    const { result } = renderHook(() => useTenant(), { wrapper });

    act(() => {
      result.current.setTenant(mockEstablishmentSummaryActive);
    });

    expect(result.current.tenant).toEqual(mockEstablishmentSummaryActive);
    expect(sessionStorage.getItem(TENANT_STORAGE_KEY)).toBe(
      JSON.stringify(mockEstablishmentSummaryActive)
    );
  });

  it('clears tenant and removes from sessionStorage', () => {
    sessionStorage.setItem(
      TENANT_STORAGE_KEY,
      JSON.stringify(mockEstablishmentSummaryActive)
    );

    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <TenantProvider>{children}</TenantProvider>
    );

    const { result } = renderHook(() => useTenant(), { wrapper });

    act(() => {
      result.current.clearTenant();
    });

    expect(result.current.tenant).toBeNull();
    expect(sessionStorage.getItem(TENANT_STORAGE_KEY)).toBeNull();
  });
});
