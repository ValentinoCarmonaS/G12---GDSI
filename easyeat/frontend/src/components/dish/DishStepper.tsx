import React from 'react';

export type DishEditorTab = 'identity' | 'recipe' | 'allergens' | 'review';

export interface DishStepperProps {
  activeTab: DishEditorTab;
  onTabChange: (tab: DishEditorTab) => void;
  hasDishId?: boolean;
  status?: string;
  isLocked?: boolean;
  completedSteps?: Partial<Record<DishEditorTab, boolean>>;
}

const STEPS: { id: DishEditorTab; number: string; label: string }[] = [
  { id: 'identity', number: '01', label: 'Identidad del plato' },
  { id: 'recipe', number: '02', label: 'Receta (BOM)' },
  { id: 'allergens', number: '03', label: 'Alérgenos' },
  { id: 'review', number: '04', label: 'Revisión y Envío' },
];

export const DishStepper: React.FC<DishStepperProps> = ({
  activeTab,
  onTabChange,
  completedSteps,
}) => {
  return (
    <nav aria-label="Pasos de edición del plato" className="mb-6">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-2">
        {STEPS.map((step) => {
          const isActive = activeTab === step.id;
          const isCompleted = Boolean(completedSteps?.[step.id]);

          return (
            <button
              key={step.id}
              type="button"
              onClick={() => onTabChange(step.id)}
              aria-current={isActive ? 'step' : undefined}
              className={`min-h-[48px] px-4 py-3 border-2 border-[#0A0A0A] text-left flex items-center justify-between gap-2 transition-colors ${
                isActive
                  ? 'bg-[#0A0A0A] text-[#FFFFFF]'
                  : 'bg-[#FFFFFF] text-[#0A0A0A] hover:bg-[#F5F5F4]'
              }`}
            >
              <span className="flex items-center gap-2 min-w-0">
                <span
                  className={`font-mono text-xs font-bold px-1.5 py-0.5 border ${
                    isActive
                      ? 'border-[#FFFFFF] text-[#FFFFFF]'
                      : 'border-[#0A0A0A] bg-[#F5F5F4] text-[#0A0A0A]'
                  }`}
                >
                  {step.number}
                </span>
                <span className="text-sm font-bold uppercase tracking-wide truncate">
                  {step.label}
                </span>
              </span>
              {isCompleted && (
                <span
                  aria-label="Completado"
                  className={`font-mono text-xs font-bold shrink-0 ${
                    isActive ? 'text-[#009866]' : 'text-[#007A52]'
                  }`}
                >
                  OK
                </span>
              )}
            </button>
          );
        })}
      </div>
    </nav>
  );
};

export default DishStepper;
