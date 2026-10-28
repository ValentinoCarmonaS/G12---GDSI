import React from 'react';
import { AlertTriangle } from 'lucide-react';
import { EstablishmentSummary } from '@/types/dinerCatalog';

export interface EstablishmentSwitchModalProps {
  isOpen: boolean;
  targetEstablishment: EstablishmentSummary | null;
  onConfirm: () => void;
  onCancel: () => void;
}

export const EstablishmentSwitchModal: React.FC<EstablishmentSwitchModalProps> = ({
  isOpen,
  targetEstablishment,
  onConfirm,
  onCancel,
}) => {
  if (!isOpen || !targetEstablishment) {
    return null;
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="switch-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in"
    >
      <div className="bg-white dark:bg-gray-800 rounded-2xl max-w-md w-full p-6 shadow-2xl border border-gray-100 dark:border-gray-700">
        <div className="flex items-center gap-3 mb-4 text-amber-600 dark:text-amber-400">
          <div className="p-2.5 bg-amber-50 dark:bg-amber-950/40 rounded-xl">
            <AlertTriangle className="w-6 h-6" />
          </div>
          <h3
            id="switch-modal-title"
            className="text-lg font-bold text-gray-900 dark:text-gray-100"
          >
            ¿Cambiar de restaurante?
          </h3>
        </div>

        <p className="text-sm text-gray-600 dark:text-gray-300 leading-relaxed mb-6">
          Tenés productos de otro local en tu carrito. Si cambiás a{' '}
          <span className="font-semibold text-gray-900 dark:text-white">
            {targetEstablishment.name}
          </span>
          , se vaciará tu carrito para evitar mezclar pedidos de distintos restaurantes.
        </p>

        <div className="flex items-center justify-end gap-3">
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2.5 text-sm font-medium text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 rounded-xl transition-colors cursor-pointer"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="px-4 py-2.5 text-sm font-medium text-white bg-amber-600 hover:bg-amber-700 rounded-xl shadow-md transition-colors cursor-pointer"
          >
            Cambiar de local
          </button>
        </div>
      </div>
    </div>
  );
};

