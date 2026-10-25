import React from 'react';
import { Navigate, useLocation, Outlet } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { Role } from '../types/auth';
import { getRoleDefaultRoute } from './postAuthRouting';

interface RoleRouteGuardProps {
  allowedRoles: Role[];
  children?: React.ReactNode;
}

export const RoleRouteGuard: React.FC<RoleRouteGuardProps> = ({
  allowedRoles,
  children,
}) => {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();

  // 1. Unauthenticated perimeter check (FR-011)
  if (!isAuthenticated || !user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  // 1.5 Mandatory first-login password change check (FR-003, FR-030)
  if (user.mustChangePassword && location.pathname !== '/first-login-password-change') {
    return <Navigate to="/first-login-password-change" replace />;
  }

  // 2. Cross-role isolation check (FR-012, SC-003)
  if (!allowedRoles.includes(user.role)) {
    const defaultRoute = getRoleDefaultRoute(user.role);
    return <Navigate to={defaultRoute} replace />;
  }

  // 3. Authorized access granted
  return children ? <>{children}</> : <Outlet />;
};

export default RoleRouteGuard;
