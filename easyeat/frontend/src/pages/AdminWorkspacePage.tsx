import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { authApi } from '../api/authApi';
import { staffApi } from '../api/staffApi';
import { inventoryApi } from '../api/inventoryApi';
import { adminNotificationApi } from '../api/adminNotificationApi';
import { useAuth } from '../hooks/useAuth';
import type { AdminNotificationResponse } from '../types/notification';

export const AdminWorkspacePage: React.FC = () => {
  const { user } = useAuth();
  const [staffCount, setStaffCount] = useState<number | null>(null);
  const [ingredientCount, setIngredientCount] = useState<number | null>(null);
  const [pendingNotifications, setPendingNotifications] = useState<AdminNotificationResponse[]>([]);
  const [pendingReviewCount, setPendingReviewCount] = useState<number | null>(null);

  useEffect(() => {
    authApi.me().catch(() => { /* handled by interceptor */ });

    staffApi
      .listStaff()
      .then((data) => setStaffCount(data.length))
      .catch(() => setStaffCount(null));

    inventoryApi
      .listIngredients()
      .then((data) => setIngredientCount(data.length))
      .catch(() => setIngredientCount(null));

    adminNotificationApi
      .listNotifications({ unreadOnly: true })
      .then((data) => {
        setPendingNotifications(data.content || []);
        setPendingReviewCount(data.unreadCount ?? data.totalElements);
      })
      .catch(() => setPendingReviewCount(null));
  }, []);

  return (
    <WorkspaceLayout
      title="Panel de Administración"
      subtitle="Configuración global del establecimiento, usuarios y auditoría"
    >
      <div className="mb-6 bg-purple-50 border border-purple-200 rounded-lg p-4 flex items-center justify-between">
        <div>
          <h4 className="text-sm font-semibold text-purple-900">Ámbito de Administración Multi-Tenant</h4>
          <p className="text-xs text-purple-700 mt-0.5">
            Los datos y operaciones están estrictamente aislados para el establecimiento asignado.
          </p>
        </div>
        {user?.tenantId && (
          <span
            data-testid="admin-tenant-scope"
            className="text-xs font-mono bg-purple-100 text-purple-800 px-3 py-1 rounded-md border border-purple-300"
          >
            Tenant ID: {user.tenantId}
          </span>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <Link
          to="/admin/staff"
          className="bg-white p-6 rounded-lg shadow-sm border border-gray-200 hover:border-purple-300 hover:shadow transition-all block"
        >
          <h3 className="font-semibold text-gray-900 text-lg mb-2">Personal Activo</h3>
          <p className="text-3xl font-bold text-purple-600">
            {staffCount !== null ? `${staffCount}` : '--'}
          </p>
          <p className="text-xs text-gray-500 mt-1">Gestión de roles y altas →</p>
        </Link>

        <Link
          to="/admin/inventory"
          className="bg-white p-6 rounded-lg shadow-sm border border-gray-200 hover:border-purple-300 hover:shadow transition-all block"
        >
          <h3 className="font-semibold text-gray-900 text-lg mb-2">Materias Primas</h3>
          <p className="text-3xl font-bold text-gray-900">
            {ingredientCount !== null ? `${ingredientCount}` : '--'}
          </p>
          <p className="text-xs text-gray-500 mt-1">Catálogo maestro y ajustes →</p>
        </Link>

        <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
          <h3 className="font-semibold text-gray-900 text-lg mb-2">Menú y Precios</h3>
          <p className="text-3xl font-bold text-purple-600">
            {pendingReviewCount !== null ? `${pendingReviewCount}` : '--'}
          </p>
          <p className="text-xs text-gray-500 mt-1">
            {pendingReviewCount && pendingReviewCount > 0
              ? `${pendingReviewCount} platos pendientes de fijación de precio`
              : 'Sin platos pendientes de revisión'}
          </p>
        </div>

        <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
          <h3 className="font-semibold text-gray-900 text-lg mb-2">Auditoría de Sesiones</h3>
          <p className="text-3xl font-bold text-gray-900">En línea</p>
          <p className="text-xs text-gray-500 mt-1">Verificación de sesiones activas</p>
        </div>
      </div>

      {pendingNotifications.length > 0 && (
        <div className="mt-8 bg-white p-6 rounded-lg shadow-sm border border-amber-200">
          <h3 className="font-semibold text-gray-900 text-base mb-3 flex items-center gap-2">
            <span>🔔</span> Platos Enviados a Revisión (Pendientes de Precio)
          </h3>
          <div className="divide-y divide-gray-100">
            {pendingNotifications.map((notif) => (
              <div key={notif.id} className="py-3 flex items-center justify-between">
                <div>
                  <h4 className="text-sm font-semibold text-gray-900">{notif.title}</h4>
                  <p className="text-xs text-gray-600 mt-0.5">{notif.message}</p>
                </div>
                <span className="text-xs bg-amber-100 text-amber-800 px-2.5 py-1 rounded-full font-medium">
                  Pendiente
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </WorkspaceLayout>
  );
};

export default AdminWorkspacePage;
