import React from 'react';
import type { InboxTab } from '@/types/dishReview';

export interface ReviewInboxTabsProps {
  activeTab: InboxTab;
  /** Shown on every tab, so the badge never depends on the active view (FR-003). */
  pendingCount: number;
  onTabChange: (tab: InboxTab) => void;
}

/**
 * The three views of FR-002.
 *
 * The labels avoid suggesting a partition, because the views are not one: a dish rejected once
 * and later published belongs to "Publicados" and to "Historial" at the same time.
 */
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
  <div role="tablist" aria-label="Vistas de la bandeja de revisión" className="flex gap-1 border-b border-gray-200">
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
          className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
            isActive
              ? 'border-blue-600 text-blue-700'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          {tab.label}
          {/*
            Rendered only above zero. A badge showing "0" reads as a problem to investigate,
            while an absent badge reads as an empty queue — which is what it is.
          */}
          {tab.id === 'PENDING' && pendingCount > 0 && (
            <span
              data-testid="pending-count-badge"
              aria-label={`${pendingCount} platos pendientes de decisión`}
              className="ml-2 inline-flex items-center justify-center min-w-5 px-1.5 py-0.5 rounded-full bg-amber-100 text-amber-800 text-xs font-semibold border border-amber-300"
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
