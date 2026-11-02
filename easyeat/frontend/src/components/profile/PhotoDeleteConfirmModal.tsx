import React from 'react';
import { User, AlertTriangle } from 'lucide-react';

export interface PhotoDeleteConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

export const PhotoDeleteConfirmModal: React.FC<PhotoDeleteConfirmModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
}) => {
  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-labelledby="delete-avatar-title"
    >
      <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-neutral-200 animate-in fade-in zoom-in-95 duration-150">
        <div className="flex items-center gap-3 text-amber-600 mb-4">
          <div className="p-2.5 bg-amber-50 rounded-xl">
            <AlertTriangle className="w-6 h-6 text-amber-600" />
          </div>
          <h3 id="delete-avatar-title" className="text-lg font-semibold text-neutral-900">
            ¿Desea eliminar su foto de perfil?
          </h3>
        </div>

        <p className="text-sm text-neutral-600 mb-6">
          Se restablecerá el avatar institucional por defecto. La eliminación se consolidará de manera efectiva al presionar "Guardar cambios" en el formulario de datos.
        </p>

        {/* Provisional fallback preview */}
        <div className="flex flex-col items-center justify-center p-4 bg-neutral-50 rounded-xl border border-neutral-100 mb-6">
          <span className="text-xs font-medium text-neutral-500 mb-2">Vista previa provisional</span>
          <div className="w-16 h-16 rounded-full bg-neutral-200 border-2 border-dashed border-neutral-300 flex items-center justify-center text-neutral-400">
            <User className="w-8 h-8" />
          </div>
        </div>

        <div className="flex items-center justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-lg text-sm font-medium text-neutral-700 hover:bg-neutral-100 transition-colors"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => {
              onConfirm();
              onClose();
            }}
            className="px-4 py-2 rounded-lg text-sm font-medium text-white bg-red-600 hover:bg-red-700 transition-colors shadow-sm"
          >
            Confirmar eliminación
          </button>
        </div>
      </div>
    </div>
  );
};

export default PhotoDeleteConfirmModal;
