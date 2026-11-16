import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { User as UserIcon, Menu as MenuIcon, X as CloseIcon } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { profileApi } from '../../api/profileApi';
import { adminNotificationApi } from '../../api/adminNotificationApi';
import { getDinerBranding } from '../../api/brandingApi';
import { AdminNotificationBell } from '../notification/AdminNotificationBell';
import { getAuthenticatedAvatarUrl } from '../../utils/avatarUtils';
import type { AdminNotificationResponse } from '../../types/notification';

interface WorkspaceLayoutProps {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}

const ROLE_BADGE_COLORS: Record<string, string> = {
  ENCARGADO: 'bg-blue-100 text-blue-800 border-blue-200',
  CHEF: 'bg-orange-100 text-orange-800 border-orange-200',
  ADMINISTRADOR: 'bg-purple-100 text-purple-800 border-purple-200',
  COMENSAL: 'bg-green-100 text-green-800 border-green-200',
};

/**
 * C-08 (spec 005 FR-027): the Chef's operational header exposes three first-class
 * sections. "Gestionar Platos" lives here, no longer nested inside the KDS view.
 */
const CHEF_SECTIONS: ReadonlyArray<{ to: string; label: string }> = [
  { to: '/cocina', label: 'Comandas KDS' },
  { to: '/cocina/platos', label: 'Gestionar Platos' },
  { to: '/cocina/ingredientes', label: 'Ingredientes' },
];

/**
 * C-08 (spec 005 FR-030): active section derives from the current route. The KDS
 * section matches exactly so nested dish routes (`/cocina/platos/nuevo`, `/cocina/platos/:id`)
 * light up "Gestionar Platos" instead of "Comandas KDS".
 */
const isChefSectionActive = (pathname: string, to: string): boolean =>
  to === '/cocina' ? pathname === to : pathname === to || pathname.startsWith(`${to}/`);

export const WorkspaceLayout: React.FC<WorkspaceLayoutProps> = ({
  title,
  subtitle,
  children,
}) => {
  const { user, logout } = useAuth();
  const { pathname, hash } = useLocation();
  const roleColor = user?.role ? ROLE_BADGE_COLORS[user.role] || 'bg-gray-100 text-gray-800' : 'bg-gray-100 text-gray-800';

  const [notifications, setNotifications] = useState<AdminNotificationResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);

  // Chef hamburger dropdown menu state
  const [isChefMenuOpen, setIsChefMenuOpen] = useState<boolean>(false);
  const chefMenuRef = useRef<HTMLDivElement | null>(null);

  // Chef operational footer branding state
  const [commercialName, setCommercialName] = useState<string | null>(null);

  useEffect(() => {
    if (user?.role !== 'CHEF' || !user.tenantId) {
      setCommercialName(null);
      return;
    }
    let isMounted = true;
    try {
      const promise = getDinerBranding(user.tenantId);
      if (promise && typeof promise.then === 'function') {
        promise
          .then((branding) => {
            if (isMounted && branding?.fantasyName?.trim()) {
              setCommercialName(branding.fantasyName.trim());
            }
          })
          .catch(() => {
            // Fallback to null; will display short tenant ID
          });
      }
    } catch {
      // Ignored non-blocking
    }
    return () => {
      isMounted = false;
    };
  }, [user?.role, user?.tenantId]);

  // Close chef menu on route change or outside click
  useEffect(() => {
    setIsChefMenuOpen(false);
  }, [pathname]);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (chefMenuRef.current && !chefMenuRef.current.contains(e.target as Node)) {
        setIsChefMenuOpen(false);
      }
    };
    if (isChefMenuOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isChefMenuOpen]);

  const fetchAvatar = useCallback(async () => {
    if (!user || user.role === 'CHEF') {
      setAvatarUrl(null);
      return;
    }
    try {
      const data = await profileApi.getProfile();
      setAvatarUrl(data?.avatarUrl || null);
    } catch {
      setAvatarUrl(null);
    }
  }, [user]);

  useEffect(() => {
    fetchAvatar();
    const handleAvatarUpdate = () => {
      fetchAvatar();
    };
    window.addEventListener('easyeat:avatar-changed', handleAvatarUpdate);
    return () => {
      window.removeEventListener('easyeat:avatar-changed', handleAvatarUpdate);
    };
  }, [fetchAvatar]);

  const fetchNotifications = useCallback(async () => {
    if (user?.role !== 'ADMINISTRADOR') return;
    try {
      const data = await adminNotificationApi.listNotifications({ page: 0, size: 20 });
      setNotifications(data.content || []);
      setUnreadCount(data.unreadCount || 0);
    } catch {
      // Ignored for non-admin or unauthenticated
    }
  }, [user?.role]);

  useEffect(() => {
    fetchNotifications();
  }, [fetchNotifications]);

  const handleMarkAsRead = async (notificationId: string) => {
    try {
      await adminNotificationApi.markAsRead(notificationId);
      await fetchNotifications();
    } catch {
      // Ignore
    }
  };

  const handleLogout = async () => {
    await logout();
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Top operational navigation bar */}
      <header className="bg-white border-b border-gray-200 sticky top-0 z-30 shadow-xs">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3 min-w-0">
            <span className="font-extrabold text-xl text-gray-900 tracking-tight">EasyEat</span>
            {/* In Chef workspace, omit visual redundant title for KDS while keeping non-KDS titles visible (RF-017) */}
            {user?.role === 'CHEF' && title !== 'Kitchen Display System (KDS)' && (
              <>
                <span className="text-gray-300">|</span>
                <span className="font-medium text-gray-700">{title}</span>
              </>
            )}
            {user?.role !== 'CHEF' && (
              <>
                <span className="text-gray-300">|</span>
                <span className="font-medium text-gray-700">{title}</span>
              </>
            )}

            {user?.role === 'CHEF' && (
              <div className="relative ml-2" ref={chefMenuRef}>
                <button
                  type="button"
                  onClick={() => setIsChefMenuOpen((prev) => !prev)}
                  aria-expanded={isChefMenuOpen}
                  aria-haspopup="true"
                  aria-label="Menú de navegación de cocina"
                  className="flex items-center justify-center p-2 rounded-lg text-gray-700 hover:bg-gray-100 border border-gray-200 transition-colors focus:outline-none focus:ring-2 focus:ring-orange-500"
                >
                  {isChefMenuOpen ? (
                    <CloseIcon className="w-5 h-5 text-gray-800" />
                  ) : (
                    <MenuIcon className="w-5 h-5 text-gray-800" />
                  )}
                </button>

                {isChefMenuOpen && (
                  <nav
                    aria-label="Secciones de cocina"
                    className="absolute left-0 mt-2 w-56 bg-white rounded-xl shadow-lg border border-gray-200 py-1.5 z-50 animate-in fade-in zoom-in-95 duration-100"
                  >
                    {CHEF_SECTIONS.map((section) => {
                      const active = isChefSectionActive(pathname, section.to);
                      return (
                        <Link
                          key={section.to}
                          to={section.to}
                          aria-current={active ? 'page' : undefined}
                          onClick={() => setIsChefMenuOpen(false)}
                          className={`block px-4 py-2.5 text-sm transition-colors ${
                            active
                              ? 'bg-orange-50 text-orange-700 font-bold'
                              : 'text-gray-700 hover:bg-gray-50 font-medium'
                          }`}
                        >
                          {section.label}
                        </Link>
                      );
                    })}
                  </nav>
                )}
              </div>
            )}
            {user?.role === 'ADMINISTRADOR' && (
              <nav className="ml-4 hidden sm:flex items-center space-x-2 text-xs">
                <Link
                  to="/admin"
                  className="px-2.5 py-1 rounded hover:bg-gray-100 text-gray-700 font-medium"
                >
                  Panel
                </Link>
                <Link
                  to="/admin/dishes"
                  className="px-2.5 py-1 rounded hover:bg-gray-100 text-purple-700 font-semibold"
                >
                  Auditoría
                </Link>
                <Link
                  to="/admin/staff"
                  className="px-2.5 py-1 rounded hover:bg-gray-100 text-purple-700 font-semibold"
                >
                  Personal
                </Link>
                <Link
                  to="/admin/inventory"
                  className="px-2.5 py-1 rounded hover:bg-gray-100 text-purple-700 font-semibold"
                >
                  Inventario
                </Link>
              </nav>
            )}
            {user?.role === 'COMENSAL' && (
              <nav
                aria-label="Navegación de comensal"
                className="ml-2 flex min-w-0 items-center gap-1 overflow-x-auto text-xs"
              >
                <Link
                  to="/catalogo"
                  className={`px-2.5 py-1 rounded whitespace-nowrap ${
                    pathname.startsWith('/catalogo')
                      ? 'bg-green-50 text-green-700 font-semibold'
                      : 'hover:bg-gray-100 text-gray-700 font-medium'
                  }`}
                >
                  Catálogo
                </Link>
                <Link
                  to="/perfil#pedidos"
                  className={`px-2.5 py-1 rounded whitespace-nowrap ${
                    pathname === '/perfil' && hash === '#pedidos'
                      ? 'bg-green-50 text-green-700 font-semibold'
                      : 'hover:bg-gray-100 text-gray-700 font-medium'
                  }`}
                >
                  Mis pedidos
                </Link>
                <Link
                  to="/perfil"
                  className={`px-2.5 py-1 rounded whitespace-nowrap ${
                    pathname === '/perfil' && hash !== '#pedidos'
                      ? 'bg-green-50 text-green-700 font-semibold'
                      : 'hover:bg-gray-100 text-green-700 font-semibold'
                  }`}
                >
                  Mi perfil
                </Link>
              </nav>
            )}
          </div>

          <div className="flex items-center gap-4">
            {user ? (
              user.role === 'CHEF' ? (
                // For CHEF, session details and logout are relocated to the operational footer
                null
              ) : (
                <>
                  <div className="flex items-center gap-3">
                    <span
                      className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${roleColor}`}
                    >
                      {user.role}
                    </span>

                    {user.tenantId && (
                      <span
                        data-testid="tenant-badge"
                        className="px-2 py-0.5 rounded text-xs font-mono bg-gray-100 text-gray-700 border border-gray-200 hidden sm:inline"
                        title={`Tenant: ${user.tenantId}`}
                      >
                        Local: {user.tenantId.slice(0, 8)}
                      </span>
                    )}

                    <span className="text-sm text-gray-600 hidden md:inline">{user.email}</span>

                    {/* Reactive user avatar with institutional fallback */}
                    <Link
                      to="/perfil"
                      title="Mi perfil"
                      aria-label="Avatar de usuario"
                      className="flex items-center justify-center w-8 h-8 rounded-full overflow-hidden border border-gray-200 bg-gray-100 hover:ring-2 hover:ring-amber-500 transition-all shrink-0"
                    >
                      {avatarUrl ? (
                        <img
                          src={getAuthenticatedAvatarUrl(avatarUrl)}
                          alt="Avatar"
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <UserIcon className="w-4 h-4 text-gray-500" />
                      )}
                    </Link>

                    {user.role === 'ADMINISTRADOR' && (
                      <AdminNotificationBell
                        notifications={notifications}
                        unreadCount={unreadCount}
                        onMarkAsRead={handleMarkAsRead}
                        onRefresh={fetchNotifications}
                      />
                    )}
                  </div>

                  <button
                    onClick={handleLogout}
                    className="text-sm font-medium text-gray-600 hover:text-red-600 px-3 py-1.5 rounded-md hover:bg-red-50 transition-colors"
                  >
                    Cerrar sesión
                  </button>
                </>
              )
            ) : (
              <div className="flex items-center gap-2">
                <Link
                  to="/login"
                  className="text-sm font-medium text-gray-700 hover:text-gray-900 px-3 py-1.5 rounded-md hover:bg-gray-100 transition-colors"
                >
                  Iniciar sesión
                </Link>
                <Link
                  to="/register"
                  className="text-sm font-medium text-white bg-green-600 hover:bg-green-700 px-3 py-1.5 rounded-md transition-colors shadow-xs"
                >
                  Registrarse
                </Link>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Main operational workspace body */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {subtitle && (
          <div className="mb-6">
            <p className="text-sm text-gray-500">{subtitle}</p>
          </div>
        )}
        {children}
      </main>

      {/* Operational footer dedicated to CHEF role */}
      {user?.role === 'CHEF' && (
        <footer
          role="contentinfo"
          className="bg-white border-t border-gray-200 mt-auto py-3 px-4 sm:px-6 lg:px-8 shadow-xs"
        >
          <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-gray-600">
            <div className="flex items-center gap-3">
              <span
                data-testid="tenant-badge"
                className="px-2.5 py-1 rounded-md font-medium bg-gray-100 text-gray-800 border border-gray-200"
                title={user.tenantId ? `Tenant: ${user.tenantId}` : undefined}
              >
                Local: {commercialName || (user.tenantId ? user.tenantId.slice(0, 8) : 'General')}
              </span>
              <span className={`px-2 py-0.5 rounded-full font-bold border ${roleColor}`}>
                CHEF
              </span>
              <span className="font-medium text-gray-700">{user.email}</span>
            </div>

            <div>
              <button
                type="button"
                onClick={handleLogout}
                className="font-medium text-gray-600 hover:text-red-600 px-3 py-1.5 rounded-md hover:bg-red-50 transition-colors"
              >
                Cerrar sesión
              </button>
            </div>
          </div>
        </footer>
      )}
    </div>
  );
};

export default WorkspaceLayout;
