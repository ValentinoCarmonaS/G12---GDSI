import React, { useEffect } from 'react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { authApi } from '../api/authApi';
import { startTicketPreparation } from '../api/kitchenKdsApi';
import { KitchenKanbanBoard } from '../components/kds/KitchenKanbanBoard';
import { useKdsQueuePolling } from '../hooks/useKdsQueuePolling';

export const KitchenWorkspacePage: React.FC = () => {
  // KDS active queue polling for Kanban board
  const { tickets, refetch: refetchQueue } = useKdsQueuePolling();

  useEffect(() => {
    authApi.me().catch(() => { /* handled by interceptor */ });
  }, []);

  const handleStartPreparation = async (ticketId: string) => {
    await startTicketPreparation(ticketId);
    await refetchQueue();
  };

  return (
    <WorkspaceLayout title="Kitchen Display System (KDS)">
      {/* Main KDS Kanban Board (T025, CHF-3.2.1, US6) */}
      <div className="w-full" data-testid="kds-kanban-section">
        <KitchenKanbanBoard
          tickets={tickets}
          onStartPreparation={handleStartPreparation}
        />
      </div>
    </WorkspaceLayout>
  );
};

export default KitchenWorkspacePage;
