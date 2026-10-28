import React from 'react';

export type DishEditorTab = 'identity' | 'recipe' | 'allergens' | 'review';

export interface DishStepperProps {
  activeTab: DishEditorTab;
  onTabChange: (tab: DishEditorTab) => void;
  hasDishId: boolean;
  status?: string;
  isLocked?: boolean;
}

export const DishStepper: React.FC<DishStepperProps> = ({
  activeTab,
  onTabChange,
  hasDishId,
}) => {
  const steps: { id: DishEditorTab; label: string; requiresDish: boolean }[] = [
    { id: 'identity', label: 'Paso 1: Identidad del plato', requiresDish: false },
    { id: 'recipe', label: 'Paso 2: Receta (BOM)', requiresDish: true },
    { id: 'allergens', label: 'Paso 3: Alérgenos', requiresDish: true },
    { id: 'review', label: 'Paso 4: Revisión y Envío', requiresDish: true },
  ];

  return (
    <div className="flex border-b border-gray-800 mb-6 gap-2 overflow-x-auto">
      {steps.map((step) => {
        const isActive = activeTab === step.id;
        const isDisabled = step.requiresDish && !hasDishId;

        return (
          <button
            key={step.id}
            type="button"
            disabled={isDisabled}
            onClick={() => onTabChange(step.id)}
            className={`py-2.5 px-4 text-sm font-semibold border-b-2 whitespace-nowrap transition-colors ${
              isActive
                ? 'border-amber-500 text-amber-400'
                : 'border-transparent text-gray-400 hover:text-gray-200 disabled:opacity-40 disabled:cursor-not-allowed'
            }`}
            title={isDisabled ? 'Creá el borrador básico primero' : undefined}
          >
            {step.label}
          </button>
        );
      })}
    </div>
  );
};

export default DishStepper;
