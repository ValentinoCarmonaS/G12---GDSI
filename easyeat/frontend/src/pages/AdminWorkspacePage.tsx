import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { authApi } from '../api/authApi';
import { staffApi } from '../api/staffApi';
import { inventoryApi } from '../api/inventoryApi';
import { useAuth } from '../hooks/useAuth';

export const AdminWorkspacePage: React.FC = () => {
  const { user } = useAuth();
  const [staffCount, setStaffCount] = useState<number | null>(null);
  const [ingredientCount, setIngredientCount] = useState<number | null>(null);

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
          <p className="text-3xl font-bold text-gray-900">Activo</p>
          <p className="text-xs text-gray-500 mt-1">Catálogo y promociones</p>
        </div>

        <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
          <h3 className="font-semibold text-gray-900 text-lg mb-2">Auditoría de Sesiones</h3>
          <p className="text-3xl font-bold text-gray-900">En línea</p>
          <p className="text-xs text-gray-500 mt-1">Verificación de sesiones activas</p>
        </div>
      </div>
    </WorkspaceLayout>
  );
};

export default AdminWorkspacePage;
