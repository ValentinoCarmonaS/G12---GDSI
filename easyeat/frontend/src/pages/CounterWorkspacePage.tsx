import React, { useCallback, useEffect, useState } from 'react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { authApi } from '../api/authApi';
import { useAuth } from '../hooks/useAuth';
import { useCounterOrdersPolling } from '../hooks/useCounterOrdersPolling';
import { CounterConnectionBanner } from '../components/counter/CounterConnectionBanner';
import { CounterTicketCard } from '../components/counter/CounterTicketCard';
import { PackageCheck } from 'lucide-react';

export const CounterWorkspacePage: React.FC = () => {
  const { user } = useAuth();
  const [expandedTicketIds, setExpandedTicketIds] = useState<Set<string>>(
    () => new Set<string>()
  );

  const { orders, isDisconnected, serverOffsetMs } = useCounterOrdersPolling({
    establishmentId: user?.tenantId ?? undefined,
  });

  useEffect(() => {
    // Validate token_version against the backend on workspace mount.
    authApi.me().catch(() => { /* handled by interceptor */ });
  }, []);

  const handleToggleCard = useCallback((ticketId: string) => {
    setExpandedTicketIds((prev) => {
      const next = new Set(prev);
      if (next.has(ticketId)) {
        next.delete(ticketId);
      } else {
        next.add(ticketId);
      }
      return next;
    });
  }, []);

  return (
    <WorkspaceLayout
      title="Mostrador y Despacho"
      subtitle="Punto de atención para gestión y entrega de pedidos en salón"
    >
      {/* Header status bar with non-blocking connection banner (RF-018) */}
      <div className="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div className="flex items-center gap-2">
          <PackageCheck className="w-5 h-5 text-emerald-600" />
          <h2 className="text-base font-bold text-gray-900">
            Pedidos listos para retiro
          </h2>
          <span
            data-testid="counter-ready-count"
            className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-50 text-emerald-800 border border-emerald-200"
          >
            {orders.length}
          </span>
        </div>

        <CounterConnectionBanner isDisconnected={isDisconnected} />
      </div>

      {/* Empty state or deterministic grid of collapsible cards (RF-005..RF-019) */}
      {orders.length === 0 ? (
        <div
          data-testid="counter-empty-state"
          className="bg-white rounded-2xl border border-gray-200 p-10 text-center shadow-sm"
        >
          <p className="text-sm font-medium text-gray-600">
            No hay pedidos listos para entrega
          </p>
        </div>
      ) : (
        <div
          data-testid="counter-orders-grid"
          className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5 items-start"
        >
          {orders.map((order) => (
            <CounterTicketCard
              key={order.ticketId}
              order={order}
              isExpanded={expandedTicketIds.has(order.ticketId)}
              onToggle={handleToggleCard}
              serverOffsetMs={serverOffsetMs}
            />
          ))}
        </div>
      )}
    </WorkspaceLayout>
  );
};

export default CounterWorkspacePage;
