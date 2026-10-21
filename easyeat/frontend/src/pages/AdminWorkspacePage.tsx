import React from 'react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';

export const AdminWorkspacePage: React.FC = () => {
  return (
    <WorkspaceLayout
      title="Panel de Administración"
      subtitle="Configuración global del establecimiento, usuarios y auditoría"
    >
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
          <h3 className="font-semibold text-gray-900 text-lg mb-2">Personal Activo</h3>
          <p className="text-3xl font-bold text-purple-600">--</p>
          <p className="text-xs text-gray-500 mt-1">Gestión de roles y altas</p>
        </div>

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
