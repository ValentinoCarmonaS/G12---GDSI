import React from 'react';

export interface CatalogMasterToggleProps {
  activeRestrictionsCount: number;
  isFilterActive: boolean;
  onToggle: (active: boolean) => void;
}

export const CatalogMasterToggle: React.FC<CatalogMasterToggleProps> = ({
  activeRestrictionsCount,
  isFilterActive,
  onToggle,
}) => {
  // FR-025: For diners with zero configured restrictions, completely hide the master filter toggle
  if (activeRestrictionsCount === 0) {
    return null;
  }

  return (
    <div
      data-testid="catalog-master-toggle-container"
      className="flex items-center justify-between p-4 bg-amber-50 border border-amber-200 rounded-lg mb-6"
    >
      <div className="flex flex-col">
        <span className="text-sm font-semibold text-gray-900">
          Filtrar por mis restricciones de perfil ({activeRestrictionsCount})
        </span>
        <span className="text-xs text-gray-600">
          {isFilterActive
            ? 'Mostrando únicamente platos aptos para tu dieta'
            : 'Mostrando menú completo con advertencias visuales'}
        </span>
      </div>

      <button
        type="button"
        role="switch"
        aria-checked={isFilterActive}
        onClick={() => onToggle(!isFilterActive)}
        data-testid="catalog-master-toggle-button"
        className={`relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2 ${
          isFilterActive ? 'bg-amber-600' : 'bg-gray-300'
        }`}
      >
        <span className="sr-only">Activar o desactivar filtro dietario automático</span>
        <span
          className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
            isFilterActive ? 'translate-x-5' : 'translate-x-0'
          }`}
        />
      </button>
    </div>
  );
};

export default CatalogMasterToggle;
