import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { dishApi } from '@/api/dishApi';
import { CostAuditPanel } from '@/components/dish/CostAuditPanel';
import { WorkspaceLayout } from '@/components/common/WorkspaceLayout';
import { FeedbackState, LoadingSkeleton } from '@/components/primitives';
import type { FoodCostAuditResponse } from '@/types/dish';

export const DishCostAuditPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [auditData, setAuditData] = useState<FoodCostAuditResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fetchAudit = useCallback(async () => {
    if (!id) return;
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await dishApi.getFoodCostAudit(id);
      setAuditData(data);
    } catch (err: any) {
      if (err.response?.status === 409) {
        setErrorMessage(
          'El plato debe estar en estado PENDING_PRICE (enviado a revisión) para auditar sus costos.'
        );
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
  }, [id]);

  useEffect(() => {
    void fetchAudit();
  }, [fetchAudit]);

  return (
    <WorkspaceLayout
      title="Auditoría Financiera de Costos"
      subtitle="Evaluación de costo de materia prima (Food Cost) y etiquetas dietarias para aprobación de precios"
    >
      <div className="mb-4">
        <Link
          to="/admin"
          className="inline-flex min-h-[44px] items-center gap-1.5 text-sm font-medium text-ee-primary hover:text-ee-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring rounded-ee-sm transition-colors duration-150 motion-reduce:transition-none"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          <span>Volver al Panel de Administración</span>
        </Link>
      </div>

      {isLoading ? (
        <div aria-busy="true" className="space-y-4">
          <span className="sr-only">
            Calculando costos y consultando catálogo de materias primas...
          </span>
          <LoadingSkeleton variant="card" count={3} />
        </div>
      ) : errorMessage ? (
        <FeedbackState
          variant="danger"
          title="No se pudo realizar la auditoría"
          description={errorMessage}
          actionLabel="Reintentar"
          onAction={fetchAudit}
        />
      ) : auditData ? (
        <CostAuditPanel auditData={auditData} onRefresh={fetchAudit} />
      ) : null}
    </WorkspaceLayout>
  );
};

export default DishCostAuditPage;
