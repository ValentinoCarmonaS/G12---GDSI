import { useContext } from 'react';
import { RoleThemeContext } from './ThemeScopeProvider';
import type { UseDataVizTokens, UseRoleTheme } from './types';

export const useRoleTheme: UseRoleTheme = () => {
  const context = useContext(RoleThemeContext);
  if (!context) {
    throw new Error('useRoleTheme must be used within a ThemeScopeProvider');
  }
  return context;
};

export const useDataVizTokens: UseDataVizTokens = () => {
  return useRoleTheme().dataViz;
};
