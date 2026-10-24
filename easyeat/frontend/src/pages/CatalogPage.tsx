import React, { useEffect } from 'react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { authApi } from '../api/authApi';

export const CatalogPage: React.FC = () => {
  useEffect(() => {
    authApi.me().catch(() => { /* handled by interceptor */ });
  }, []);

  return (
    <WorkspaceLayout
      title="Catálogo de Productos"
      subtitle="Vista de comensal y fallback del sistema"
    >
      <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200 text-center py-12">
        <h3 className="text-xl font-bold text-gray-900 mb-2">Bienvenido a EasyEat</h3>
        <p className="text-gray-600 max-w-md mx-auto">
          Podés explorar los platos y productos disponibles en el comedor.
        </p>
      </div>
    </WorkspaceLayout>
  );
};

export default CatalogPage;
