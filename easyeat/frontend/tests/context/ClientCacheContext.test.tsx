import { describe, it, expect, beforeEach, vi } from 'vitest';
import React from 'react';
import { renderHook, act } from '@testing-library/react';
import {
  ClientCacheProvider,
  ClientSWRStore,
  useClientCache,
} from '@/context/ClientCacheContext';
import { AuthProvider, useAuth } from '@/context/AuthContext';

function createMockJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.mock-signature`;
}

describe('ClientSWRStore and ClientCacheContext (T008)', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('deduplicates concurrent inFlight fetches for the same key and cleans up in finally', async () => {
    const store = new ClientSWRStore();
    let resolveFn!: (value: { ok: boolean }) => void;
    const fetcher = vi.fn(
      () =>
        new Promise<{ ok: boolean }>((resolve) => {
          resolveFn = resolve;
        })
    );

    const p1 = store.fetchDeduped('profile:me', fetcher);
    const p2 = store.fetchDeduped('profile:me', fetcher);

    expect(p1).toBe(p2);
    expect(fetcher).toHaveBeenCalledTimes(1);

    resolveFn({ ok: true });
    const [r1, r2] = await Promise.all([p1, p2]);
    expect(r1).toEqual({ ok: true });
    expect(r2).toEqual({ ok: true });
    expect(store.get<{ ok: boolean }>('profile:me')?.data).toEqual({ ok: true });

    // Subsequent fetch after completion invokes fetcher again
    fetcher.mockResolvedValueOnce({ ok: false });
    const r3 = await store.fetchDeduped('profile:me', fetcher);
    expect(r3).toEqual({ ok: false });
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('notifies reactive subscribers on set, fetchDeduped and invalidate, and supports prefix invalidation', async () => {
    const store = new ClientSWRStore();
    const listener = vi.fn();
    const unsubscribe = store.subscribe('catalog:t1:guest', listener);

    store.set('catalog:t1:guest', { dishes: [1] });
    store.set('catalog:t2:guest', { dishes: [2] });
    store.set('profile:me', { name: 'Valentino' });

    expect(listener).toHaveBeenCalledTimes(1);
    expect(store.get('catalog:t1:guest')?.data).toEqual({ dishes: [1] });

    store.invalidate('catalog:', { prefix: true });

    expect(store.get('catalog:t1:guest')).toBeUndefined();
    expect(store.get('catalog:t2:guest')).toBeUndefined();
    expect(store.get('profile:me')?.data).toEqual({ name: 'Valentino' });
    expect(listener).toHaveBeenCalledTimes(2);

    unsubscribe();
    store.set('catalog:t1:guest', { dishes: [3] });
    expect(listener).toHaveBeenCalledTimes(2);
  });

  it('tracks mediaVersions and clears entries, inFlight and mediaVersions on clearAll', () => {
    const store = new ClientSWRStore();
    store.set('profile:me', { id: 'u1' });
    store.setMediaVersion('dish:d1', 123456);

    expect(store.getMediaVersion('dish:d1')).toBe(123456);

    store.clearAll();

    expect(store.get('profile:me')).toBeUndefined();
    expect(store.getMediaVersion('dish:d1')).toBeUndefined();
  });

  it('provides isolated ephemeral store when used outside ClientCacheProvider', () => {
    const { result: first } = renderHook(() => useClientCache());
    const { result: second } = renderHook(() => useClientCache());

    first.current.set('profile:me', { id: 'isolated' });

    expect(first.current.get('profile:me')?.data).toEqual({ id: 'isolated' });
    expect(second.current.get('profile:me')).toBeUndefined();
  });

  it('synchronously clears ClientCacheProvider from AuthContext on clearAuth, logout, user switch and easyeat:session-expired', async () => {
    const tokenUser1 = createMockJwt({
      sub: 'user-1',
      email: 'u1@test.com',
      role: 'COMENSAL',
      exp: Math.floor(Date.now() / 1000) + 3600,
    });
    const tokenUser2 = createMockJwt({
      sub: 'user-2',
      email: 'u2@test.com',
      role: 'COMENSAL',
      exp: Math.floor(Date.now() / 1000) + 3600,
    });

    sessionStorage.setItem('access_token', tokenUser1);

    const wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
      <ClientCacheProvider>
        <AuthProvider>{children}</AuthProvider>
      </ClientCacheProvider>
    );

    const { result } = renderHook(
      () => ({ auth: useAuth(), cache: useClientCache() }),
      { wrapper }
    );

    // 1. User switch via setToken purges cache synchronously
    act(() => {
      result.current.cache.set('profile:me', { id: 'user-1' });
      result.current.cache.setMediaVersion('dish:1', 999);
      result.current.auth.setToken(tokenUser2);
    });
    expect(result.current.cache.get('profile:me')).toBeUndefined();
    expect(result.current.cache.getMediaVersion('dish:1')).toBeUndefined();

    // 2. Same user setToken preserves cache
    act(() => {
      result.current.cache.set('profile:me', { id: 'user-2' });
      result.current.auth.setToken(tokenUser2);
    });
    expect(result.current.cache.get('profile:me')?.data).toEqual({ id: 'user-2' });

    // 3. easyeat:session-expired event purges cache synchronously
    act(() => {
      window.dispatchEvent(new Event('easyeat:session-expired'));
    });
    expect(result.current.cache.get('profile:me')).toBeUndefined();

    // 4. clearAuth purges cache synchronously
    act(() => {
      result.current.auth.setToken(tokenUser2);
      result.current.cache.set('establishments:all', [{ id: 'e1' }]);
      result.current.auth.clearAuth();
    });
    expect(result.current.cache.get('establishments:all')).toBeUndefined();
  });
});
