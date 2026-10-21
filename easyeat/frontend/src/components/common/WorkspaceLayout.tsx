import React from 'react';
import { useAuth } from '../../hooks/useAuth';

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

export const WorkspaceLayout: React.FC<WorkspaceLayoutProps> = ({
  title,
  subtitle,
  children,
}) => {
  const { user, logout } = useAuth();
  const roleColor = user?.role ? ROLE_BADGE_COLORS[user.role] || 'bg-gray-100 text-gray-800' : 'bg-gray-100 text-gray-800';

  const handleLogout = async () => {
    await logout();
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Top operational navigation bar */}
      <header className="bg-white border-b border-gray-200 sticky top-0 z-30 shadow-xs">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="font-extrabold text-xl text-gray-900 tracking-tight">EasyEat</span>
            <span className="text-gray-300">|</span>
            <span className="font-medium text-gray-700">{title}</span>
          </div>

          <div className="flex items-center gap-4">
            {user && (
              <div className="flex items-center gap-3">
                <span
                  className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${roleColor}`}
                >
                  {user.role}
                </span>

                {user.tenantId && (
                  <span className="hidden sm:inline-block text-xs text-gray-500 bg-gray-100 px-2 py-0.5 rounded">
                    Establecimiento: {user.tenantId.slice(0, 8)}...
                  </span>
                )}

                <span className="text-sm text-gray-600 hidden md:inline">{user.email}</span>
              </div>
            )}

            <button
              onClick={handleLogout}
              className="text-sm font-medium text-gray-600 hover:text-red-600 px-3 py-1.5 rounded-md hover:bg-red-50 transition-colors"
            >
              Cerrar sesión
            </button>
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
    </div>
  );
};

export default WorkspaceLayout;
