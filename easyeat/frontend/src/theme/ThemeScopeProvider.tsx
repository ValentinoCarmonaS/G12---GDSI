import React, { createContext, useContext, useLayoutEffect, useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { resolveThemeForRoute } from './routeThemeResolver';
import { getThemeContract } from './themeRegistry';
import type { RoleThemeContextValue } from './types';

export const RoleThemeContext = createContext<RoleThemeContextValue | undefined>(undefined);

function extractFromPath(state: unknown): string | null {
  if (!state || typeof state !== 'object') {
    return null;
  }
  const from = (state as { from?: unknown }).from;
  if (typeof from === 'string') {
    return from;
  }
  if (from && typeof from === 'object' && typeof (from as { pathname?: unknown }).pathname === 'string') {
    return (from as { pathname: string }).pathname;
  }
  return null;
}

export const ThemeScopeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const location = useLocation();
  const auth = useContext(AuthContext);
  const userRole = auth?.user?.role ?? null;
  const fromPath = extractFromPath(location.state);

  const resolved = useMemo(
    () =>
      resolveThemeForRoute({
        pathname: location.pathname,
        userRole,
        fromPath,
      }),
    [location.pathname, userRole, fromPath]
  );

  const contextValue = useMemo<RoleThemeContextValue>(() => {
    const contract = getThemeContract(resolved.themeId);
    return {
      themeId: resolved.themeId,
      contract,
      dataViz: contract.dataViz,
      resolutionReason: resolved.reason,
    };
  }, [resolved.themeId, resolved.reason]);

  useLayoutEffect(() => {
    document.documentElement.setAttribute('data-theme', resolved.themeId);
  }, [resolved.themeId]);

  return (
    <RoleThemeContext.Provider value={contextValue}>
      <div
        data-testid="theme-scope-root"
        data-theme={resolved.themeId}
        className="min-h-screen bg-ee-surface text-ee-ink font-ee-sans"
      >
        {children}
      </div>
    </RoleThemeContext.Provider>
  );
};
