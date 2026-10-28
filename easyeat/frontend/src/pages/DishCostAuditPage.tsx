import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { dishApi } from '@/api/dishApi';
import { CostAuditPanel } from '@/components/dish/CostAuditPanel';
import { WorkspaceLayout } from '@/components/common/WorkspaceLayout';
import type { FoodCostAuditResponse } from '@/types/dish';

export const DishCostAuditPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [auditData, setAuditData] = useState<FoodCostAuditResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fetchAudit = async () => {
    if (!id) return;
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await dishApi.getFoodCostAudit(id);
      setAuditData(data);
    } catch (err: any) {
      if (err.response?.status === 409) {
        setErrorMessage('El plato debe estar en estado PENDING_PRICE (enviado a revisión) para auditar sus costos.');
      } else if (err.response?.status === 400) {
        setErrorMessage('El plato no tiene ingredientes asignados en su receta técnica.');
      } else if (err.response?.status === 403) {
        setErrorMessage('Acceso denegado: se requieren permisos de Administrador.');
      } else {
        setErrorMessage('Ocurrió un error al cargar la auditoría de Food Cost.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchAudit();
  }, [id]);

  return (
    <WorkspaceLayout
      title="Auditoría Financiera de Costos"
      subtitle="Evaluación de costo de materia prima (Food Cost) y etiquetas dietarias para aprobación de precios"
    >
      <div className="mb-4">
        <Link
          to="/admin"
          className="text-sm font-medium text-purple-600 hover:text-purple-700 transition"
        >
          ← Volver al Panel de Administración
        </Link>
      </div>

      {isLoading ? (
        <div className="flex justify-center items-center py-16 text-gray-500">
          Calculando costos y consultando catálogo de materias primas...
        </div>
      ) : errorMessage ? (
        <div className="p-4 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
          <p className="font-semibold mb-1">No se pudo realizar la auditoría</p>
          <p>{errorMessage}</p>
        </div>
      ) : auditData ? (
        <CostAuditPanel auditData={auditData} onRefresh={fetchAudit} />
      ) : null}
    </WorkspaceLayout>
  );
};

export default DishCostAuditPage;

