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
import { ChefIngredientsPage } from '../pages/ChefIngredientsPage';
import { ChefDishesPage } from '../pages/ChefDishesPage';
import { ChefDishEditorPage } from '../pages/ChefDishEditorPage';
import { AdminWorkspacePage } from '../pages/AdminWorkspacePage';
import { StaffManagementPage } from '../pages/StaffManagementPage';
import { InventoryManagementPage } from '../pages/InventoryManagementPage';
import { CatalogPage } from '../pages/CatalogPage';
import { FirstLoginPasswordPage } from '../pages/FirstLoginPasswordPage';
import { ProfilePage } from '../pages/ProfilePage';
import { DishCostAuditPage } from '../pages/DishCostAuditPage';
import { VerifyEmailPage } from '../pages/VerifyEmailPage';
import { EstablishmentDiscoveryPage } from '../pages/EstablishmentDiscoveryPage';

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
        path="/cocina/ingredientes"
        element={
          <RoleRouteGuard allowedRoles={['CHEF']}>
            <ChefIngredientsPage />
          </RoleRouteGuard>
        }
      />
      <Route path="/kds/ingredients" element={<Navigate to="/cocina/ingredientes" replace />} />

      {/* Role-Protected Dish & Recipe Management (CHEF) */}
      <Route
        path="/cocina/platos"
        element={
          <RoleRouteGuard allowedRoles={['CHEF']}>
            <ChefDishesPage />
          </RoleRouteGuard>
        }
      />
      <Route
        path="/cocina/platos/nuevo"
        element={
          <RoleRouteGuard allowedRoles={['CHEF']}>
            <ChefDishEditorPage />
          </RoleRouteGuard>
        }
      />
      <Route
        path="/cocina/platos/:id"
        element={
          <RoleRouteGuard allowedRoles={['CHEF']}>
            <ChefDishEditorPage />
          </RoleRouteGuard>
        }
      />

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

      {/* Role-Protected Inventory Management (ADMINISTRADOR) */}
      <Route
        path="/admin/inventory"
        element={
          <RoleRouteGuard allowedRoles={['ADMINISTRADOR']}>
            <InventoryManagementPage />
          </RoleRouteGuard>
        }
      />

      {/* Role-Protected Back-Office Administration Panel (ADMINISTRADOR) */}
      <Route
        path="/admin/dishes/:id/cost-audit"
        element={
          <RoleRouteGuard allowedRoles={['ADMINISTRADOR']}>
            <DishCostAuditPage />
          </RoleRouteGuard>
        }
      />
      <Route
        path="/admin/*"
        element={
          <RoleRouteGuard allowedRoles={['ADMINISTRADOR']}>
            <AdminWorkspacePage />
          </RoleRouteGuard>
        }
      />

      {/* Public Email Verification Callback Gateway */}
      <Route
        path="/verificar-email"
        element={<VerifyEmailPage />}
      />
      <Route path="/verify-email" element={<Navigate to="/verificar-email" replace />} />

      {/* Role-Protected Diner Profile (COMENSAL) */}
      <Route
        path="/perfil"
        element={
          <RoleRouteGuard allowedRoles={['COMENSAL']}>
            <ProfilePage />
          </RoleRouteGuard>
        }
      />
      <Route path="/profile" element={<Navigate to="/perfil" replace />} />

      {/* Role-Protected Diner Discovery Route (COMENSAL) */}
      <Route
        path="/descubrimiento"
        element={
          <RoleRouteGuard allowedRoles={['COMENSAL']}>
            <EstablishmentDiscoveryPage />
          </RoleRouteGuard>
        }
      />
      <Route path="/discovery" element={<Navigate to="/descubrimiento" replace />} />

      {/* Diner Home / Catalog View (Public for guests and diners) */}
      <Route path="/catalogo/*" element={<CatalogPage />} />
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
