import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { RoleRouteGuard } from './RoleRouteGuard';
import { getRoleDefaultRoute } from './postAuthRouting';
import { LoginPage } from '../pages/LoginPage';
import { RegisterPage } from '../pages/RegisterPage';
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage';
import { ResetPasswordPage } from '../pages/ResetPasswordPage';
import { CounterWorkspacePage } from '../pages/CounterWorkspacePage';
import { KitchenWorkspacePage } from '../pages/KitchenWorkspacePage';
import { AdminWorkspacePage } from '../pages/AdminWorkspacePage';
import { StaffManagementPage } from '../pages/StaffManagementPage';
import { CatalogPage } from '../pages/CatalogPage';
import { FirstLoginPasswordPage } from '../pages/FirstLoginPasswordPage';

export const AppRoutes: React.FC = () => {
  const { isAuthenticated, user } = useAuth();

  return (
    <Routes>
      {/* Public Authentication Gateway */}
      <Route
        path="/login"
        element={
          isAuthenticated && user ? (
            <Navigate
              to={user.mustChangePassword ? '/first-login-password-change' : getRoleDefaultRoute(user.role)}
              replace
            />
          ) : (
            <LoginPage />
          )
        }
      />

      {/* Mandatory First-Login Password Change Gateway */}
      <Route
        path="/first-login-password-change"
        element={
          !isAuthenticated || !user ? (
            <Navigate to="/login" replace />
          ) : !user.mustChangePassword ? (
            <Navigate to={getRoleDefaultRoute(user.role)} replace />
          ) : (
            <FirstLoginPasswordPage />
          )
        }
      />

      {/* Public Registration Gateway */}
      <Route
        path="/register"
        element={
          isAuthenticated && user ? (
            <Navigate to={getRoleDefaultRoute(user.role)} replace />
          ) : (
            <RegisterPage />
          )
        }
      />

      {/* Public Password Recovery Gateways */}
      <Route
        path="/forgot-password"
        element={
          isAuthenticated && user ? (
            <Navigate to={getRoleDefaultRoute(user.role)} replace />
          ) : (
            <ForgotPasswordPage />
          )
        }
      />
      <Route
        path="/reset-password"
        element={<ResetPasswordPage />}
      />
      <Route
        path="/auth/reset-password"
        element={<ResetPasswordPage />}
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
        path="/cocina/*"
        element={
          <RoleRouteGuard allowedRoles={['CHEF']}>
            <KitchenWorkspacePage />
          </RoleRouteGuard>
        }
      />
      <Route path="/kds/*" element={<Navigate to="/cocina" replace />} />

      {/* Role-Protected Staff Management (ADMINISTRADOR) */}
      <Route
        path="/admin/staff"
        element={
          <RoleRouteGuard allowedRoles={['ADMINISTRADOR']}>
            <StaffManagementPage />
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
            <Navigate
              to={user.mustChangePassword ? '/first-login-password-change' : getRoleDefaultRoute(user.role)}
              replace
            />
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
