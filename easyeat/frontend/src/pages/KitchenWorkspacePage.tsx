import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { authApi } from '../api/authApi';

export const KitchenWorkspacePage: React.FC = () => {
  useEffect(() => {
    authApi.me().catch(() => { /* handled by interceptor */ });
  }, []);

  return (
    <WorkspaceLayout
      title="Kitchen Display System (KDS)"
      subtitle="Comandas en preparación y despachos de cocina"
    >
      <div className="mb-6 bg-white p-4 rounded-lg shadow-sm border border-gray-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h4 className="text-sm font-semibold text-gray-900">Catálogo de Ingredientes y Materias Primas</h4>
          <p className="text-xs text-gray-500 mt-0.5">
            Consultá la disponibilidad de stock de insumos en tiempo real para la elaboración de platos.
          </p>
        </div>
        <Link
          to="/cocina/ingredientes"
          className="inline-flex items-center justify-center px-4 py-2 bg-orange-600 hover:bg-orange-700 text-white text-xs font-semibold rounded-md shadow-sm transition-colors whitespace-nowrap"
        >
          Ver Ingredientes →
        </Link>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-lg shadow-sm border-l-4 border-l-amber-500 border border-gray-200">
          <span className="text-xs font-bold uppercase tracking-wider text-amber-600">En Cola</span>
          <p className="text-2xl font-bold text-gray-900 mt-2">0 comandas</p>
        </div>

        <div className="bg-white p-5 rounded-lg shadow-sm border-l-4 border-l-blue-500 border border-gray-200">
          <span className="text-xs font-bold uppercase tracking-wider text-blue-600">En Preparación</span>
          <p className="text-2xl font-bold text-gray-900 mt-2">0 comandas</p>
        </div>

        <div className="bg-white p-5 rounded-lg shadow-sm border-l-4 border-l-green-500 border border-gray-200">
          <span className="text-xs font-bold uppercase tracking-wider text-green-600">Para Retirar</span>
          <p className="text-2xl font-bold text-gray-900 mt-2">0 comandas</p>
        </div>

        <div className="bg-white p-5 rounded-lg shadow-sm border-l-4 border-l-gray-400 border border-gray-200">
          <span className="text-xs font-bold uppercase tracking-wider text-gray-600">Completadas hoy</span>
          <p className="text-2xl font-bold text-gray-900 mt-2">0</p>
        </div>
      </div>
    </WorkspaceLayout>
  );
};

export default KitchenWorkspacePage;
