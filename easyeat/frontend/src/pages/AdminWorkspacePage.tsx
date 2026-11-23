import React, { useCallback, useEffect, useState } from 'react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { authApi } from '../api/authApi';
import { FinancialHealthPanel } from '../components/analytics/FinancialHealthPanel';
import { FinancialHealthChart } from '../components/analytics/FinancialHealthChart';
import { DishRankingTable } from '../components/analytics/DishRankingTable';
import type { FinancialHealthResponse } from '../types/analytics';

export const AdminWorkspacePage: React.FC = () => {
  const [financialData, setFinancialData] = useState<FinancialHealthResponse | null>(null);

  useEffect(() => {
    authApi.me().catch(() => {
      /* handled by interceptor */
    });
  }, []);

  const handleFinancialDataChange = useCallback(
    (data: FinancialHealthResponse | null) => {
      setFinancialData(data);
    },
    []
  );

  return (
    <WorkspaceLayout
      title="Panel Ejecutivo"
      subtitle="Salud financiera y demanda de platos en tiempo real"
    >
      <div className="space-y-6">
        <FinancialHealthPanel onDataChange={handleFinancialDataChange} />

        <div
          data-testid="executive-dashboard-grid"
          className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1.65fr)_minmax(0,1fr)] lg:items-start"
        >
          <div>
            {financialData && <FinancialHealthChart data={financialData} />}
          </div>
          <div>
            <DishRankingTable />
          </div>
        </div>
      </div>
    </WorkspaceLayout>
  );
};
