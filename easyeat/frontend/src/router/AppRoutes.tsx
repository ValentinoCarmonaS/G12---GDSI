import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { RoleRouteGuard } from './RoleRouteGuard';
import { getRoleDefaultRoute } from './postAuthRouting';
import { LoginPage } from '../pages/LoginPage';
import { CounterWorkspacePage } from '../pages/CounterWorkspacePage';
import { KitchenWorkspacePage } from '../pages/KitchenWorkspacePage';
import { AdminWorkspacePage } from '../pages/AdminWorkspacePage';
import { CatalogPage } from '../pages/CatalogPage';

export const AppRoutes: React.FC = () => {
  const { isAuthenticated, user } = useAuth();

  return (
    <Routes>
      {/* Public Authentication Gateway */}
      <Route
        path="/login"
        element={
          isAuthenticated && user ? (
            <Navigate to={getRoleDefaultRoute(user.role)} replace />
          ) : (
            <LoginPage />
          )
        }
      />

      {/* Role-Protected Counter / Dispatch Workspace (ENCARGADO) */}
      <Route
        path="/mostrador/*"
        element={
          <RoleRouteGuard allowedRoles={['ENCARGADO']}>
            <CounterWorkspacePage />
          </RoleRouteGuard>
        }
      />
      <Route path="/counter/*" element={<Navigate to="/mostrador" replace />} />

      {/* Role-Protected Kitchen Display System Workspace (CHEF) */}
      <Route
        path="/kds/*"
        element={
          <RoleRouteGuard allowedRoles={['CHEF']}>
            <KitchenWorkspacePage />
          </RoleRouteGuard>
        }
      />

      {/* Role-Protected Back-Office Administration Panel (ADMINISTRADOR) */}
      <Route
        path="/admin/*"
        element={
          <RoleRouteGuard allowedRoles={['ADMINISTRADOR']}>
            <AdminWorkspacePage />
          </RoleRouteGuard>
        }
      />

      {/* Diner Home / Catalog Fallback View (COMENSAL + Staff Fallback) */}
      <Route
        path="/catalogo/*"
        element={
          <RoleRouteGuard allowedRoles={['COMENSAL', 'ENCARGADO', 'CHEF', 'ADMINISTRADOR']}>
            <CatalogPage />
          </RoleRouteGuard>
        }
      />
      <Route path="/catalog/*" element={<Navigate to="/catalogo" replace />} />

      {/* Root redirect: to workspace if authenticated, or login if unauthenticated */}
      <Route
        path="/"
        element={
          isAuthenticated && user ? (
            <Navigate to={getRoleDefaultRoute(user.role)} replace />
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />

      {/* Wildcard Fallback */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
};

export default AppRoutes;
