import React from 'react';
import type { InboxTab } from '@/types/dishReview';

export interface ReviewInboxTabsProps {
  activeTab: InboxTab;
  /** Shown on every tab, so the badge never depends on the active view (FR-003). */
  pendingCount: number;
  onTabChange: (tab: InboxTab) => void;
}

const TABS: ReadonlyArray<{ id: InboxTab; label: string; hint: string }> = [
  {
    id: 'PENDING',
    label: 'En espera',
    hint: 'Platos esperando una decisión, el más antiguo primero',
  },
  {
    id: 'FINALIZED',
    label: 'Finalizados',
    hint: 'Platos que ya recibieron una decisión (aprobados, rechazados o cancelados)',
  },
];

export const ReviewInboxTabs: React.FC<ReviewInboxTabsProps> = ({
  activeTab,
  pendingCount,
  onTabChange,
}) => (
  <div
    role="tablist"
    aria-label="Vistas de la bandeja de revisión"
    className="flex gap-1 border-b border-ee-border-subtle bg-ee-surface-subtle/60 p-2"
  >
    {TABS.map((tab) => {
      const isActive = tab.id === activeTab;
      return (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={isActive}
          title={tab.hint}
          onClick={() => onTabChange(tab.id)}
          className={`inline-flex min-h-[44px] flex-1 items-center justify-center gap-2 rounded-ee-sm px-3 py-2 text-sm font-semibold transition-colors duration-150 motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring ${
            isActive
              ? 'bg-ee-primary text-ee-on-primary shadow-sm'
              : 'text-ee-text-secondary hover:bg-ee-surface-elevated hover:text-ee-text'
          }`}
        >
          <span>{tab.label}</span>
          {tab.id === 'PENDING' && pendingCount > 0 && (
            <span
              data-testid="pending-count-badge"
              aria-label={`${pendingCount} platos pendientes de decisión`}
              className={`inline-flex min-w-[22px] items-center justify-center rounded-full px-1.5 py-0.5 font-ee-mono tabular-nums text-xs font-semibold border ${
                isActive
                  ? 'bg-ee-on-primary/15 text-ee-on-primary border-ee-on-primary/40'
                  : 'bg-ee-warning-bg text-ee-warning-fg border-ee-warning-border'
              }`}
            >
              {pendingCount}
            </span>
          )}
        </button>
      );
    })}
  </div>
);

export default ReviewInboxTabs;
