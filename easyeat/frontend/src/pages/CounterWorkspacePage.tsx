import React, { useEffect } from 'react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { authApi } from '../api/authApi';

export const CounterWorkspacePage: React.FC = () => {
  useEffect(() => {
    // Validate token_version against the backend on workspace mount.
    // A 401 response (concurrent session invalidation) is caught by the
    // global session interceptor in apiClient, which clears auth and
    // redirects to /login with "Tu sesión ha expirado".
    authApi.me().catch(() => { /* handled by interceptor */ });
  }, []);

  return (
    <WorkspaceLayout
      title="Mostrador y Despacho"
      subtitle="Punto de atención para gestión y entrega de pedidos en salón"
    >
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
          <h3 className="font-semibold text-gray-900 text-lg mb-2">Pedidos por cobrar</h3>
          <p className="text-3xl font-bold text-amber-600">0</p>
          <p className="text-xs text-gray-500 mt-1">Sin turnos pendientes</p>
        </div>

        <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
          <h3 className="font-semibold text-gray-900 text-lg mb-2">Listos para entrega</h3>
          <p className="text-3xl font-bold text-green-600">0</p>
          <p className="text-xs text-gray-500 mt-1">Avisos automáticos enviados</p>
        </div>

        <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
          <h3 className="font-semibold text-gray-900 text-lg mb-2">Caja actual</h3>
          <p className="text-3xl font-bold text-gray-900">$0,00</p>
          <p className="text-xs text-gray-500 mt-1">Turno abierto</p>
        </div>
      </div>
    </WorkspaceLayout>
  );
};

export default CounterWorkspacePage;
