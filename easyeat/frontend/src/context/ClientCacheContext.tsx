import React, { createContext, useContext, useRef } from 'react';

export interface CacheEntry<T> {
  data: T;
  updatedAt: number;
  status: 'fresh';
}

export interface InvalidateOptions {
  prefix?: boolean;
}

/**
 * In-memory client SWR & in-flight deduplication store (FR-007, FR-008, D-08).
 */
export class ClientSWRStore {
  private entries = new Map<string, CacheEntry<unknown>>();
  private inFlight = new Map<string, Promise<unknown>>();
  private subscribers = new Map<string, Set<() => void>>();
  private mediaVersions = new Map<string, number>();

  get<T>(key: string): CacheEntry<T> | undefined {
    return this.entries.get(key) as CacheEntry<T> | undefined;
  }

  set<T>(key: string, data: T): void {
    this.entries.set(key, {
      data,
      updatedAt: Date.now(),
      status: 'fresh',
    });
    this.notify(key);
  }

  fetchDeduped<T>(key: string, fetcher: () => Promise<T>): Promise<T> {
    const existing = this.inFlight.get(key) as Promise<T> | undefined;
    if (existing) {
      return existing;
    }

    const promise = fetcher()
      .then((data) => {
        this.set(key, data);
        return data;
      })
      .finally(() => {
        this.inFlight.delete(key);
      });

    this.inFlight.set(key, promise);
    return promise;
  }

  invalidate(keyOrPrefix: string, options?: InvalidateOptions): void {
    if (options?.prefix) {
      const matchedKeys: string[] = [];
      for (const key of this.entries.keys()) {
        if (key.startsWith(keyOrPrefix)) {
          matchedKeys.push(key);
        }
      }
      for (const key of matchedKeys) {
        this.entries.delete(key);
        this.notify(key);
      }
      return;
    }

    if (this.entries.delete(keyOrPrefix)) {
      this.notify(keyOrPrefix);
    }
  }

  setMediaVersion(mediaKey: string, version: number): void {
    this.mediaVersions.set(mediaKey, version);
  }

  getMediaVersion(mediaKey: string): number | undefined {
    return this.mediaVersions.get(mediaKey);
  }

  subscribe(key: string, listener: () => void): () => void {
    let set = this.subscribers.get(key);
    if (!set) {
      set = new Set();
      this.subscribers.set(key, set);
    }
    set.add(listener);
    return () => {
      const current = this.subscribers.get(key);
      if (!current) return;
      current.delete(listener);
      if (current.size === 0) {
        this.subscribers.delete(key);
      }
    };
  }

  clearAll(): void {
    const keys = Array.from(this.entries.keys());
    this.entries.clear();
    this.inFlight.clear();
    this.mediaVersions.clear();
    for (const key of keys) {
      this.notify(key);
    }
  }

  private notify(key: string): void {
    const listeners = this.subscribers.get(key);
    if (!listeners) return;
    for (const listener of Array.from(listeners)) {
      listener();
    }
  }
}

export const ClientCacheContext = createContext<ClientSWRStore | undefined>(undefined);

export const ClientCacheProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const storeRef = useRef<ClientSWRStore | null>(null);
  if (!storeRef.current) {
    storeRef.current = new ClientSWRStore();
  }

  return (
    <ClientCacheContext.Provider value={storeRef.current}>
      {children}
    </ClientCacheContext.Provider>
  );
};

/**
 * Returns the shared ClientSWRStore when mounted under ClientCacheProvider, or an isolated
 * ephemeral store per hook/component instance for standalone unit tests (FR-010).
 */
export function useClientCache(): ClientSWRStore {
  const contextStore = useContext(ClientCacheContext);
  const isolatedRef = useRef<ClientSWRStore | null>(null);

  if (contextStore) {
    return contextStore;
  }

  if (!isolatedRef.current) {
    isolatedRef.current = new ClientSWRStore();
  }
  return isolatedRef.current;
}
