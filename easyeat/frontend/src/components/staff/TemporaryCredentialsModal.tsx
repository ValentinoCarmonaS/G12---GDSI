import React, { useState } from 'react';
import { KeyRound, AlertTriangle } from 'lucide-react';
import { AccessibleModal } from '../diner/AccessibleModal';

export interface TemporaryCredentialsModalProps {
  isOpen: boolean;
  onClose: () => void;
  staffName: string;
  email: string;
  temporaryCredential: string;
}

export const TemporaryCredentialsModal: React.FC<TemporaryCredentialsModalProps> = ({
  isOpen,
  onClose,
  staffName,
  email,
  temporaryCredential,
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(temporaryCredential);
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    } catch {
      // Fallback if clipboard API fails
    }
  };

  return (
    <AccessibleModal
      isOpen={isOpen}
      onClose={onClose}
      title="Credencial temporal generada"
      subtitle="Nuevo miembro del equipo"
      size="sm"
    >
      <div className="space-y-4">
        <div className="flex items-center gap-3 p-3 rounded-ee-sm bg-ee-primary/5 border border-ee-primary/20">
          <KeyRound className="w-5 h-5 text-ee-primary shrink-0" aria-hidden="true" />
          <div className="text-sm space-y-0.5 min-w-0">
            <p className="text-ee-text truncate">
              <span className="font-semibold">Empleado: </span>
              {staffName}
            </p>
            <p className="text-ee-text-muted truncate">
              <span className="font-semibold text-ee-text">Email: </span>
              {email}
            </p>
          </div>
        </div>

        <div>
          <label className="block text-xs font-semibold text-ee-text mb-1">
            Contraseña temporal (12 caracteres)
          </label>
          <div className="flex items-center gap-2">
            <div className="flex-1 bg-slate-100 border border-ee-border-subtle rounded-ee-sm px-3 py-2.5 font-ee-mono text-sm tracking-wide text-ee-text select-all overflow-x-auto">
              {temporaryCredential}
            </div>
            <button
              type="button"
              onClick={handleCopy}
              className={`min-h-[44px] px-3.5 py-2 text-xs font-semibold rounded-ee-sm transition-colors ${
                copied
                  ? 'bg-emerald-700 text-white'
                  : 'bg-ee-primary text-white hover:opacity-95'
              }`}
            >
              {copied ? '¡Copiado!' : 'Copiar'}
            </button>
          </div>
        </div>

        <div
          role="alert"
          className="p-3 text-xs rounded-ee-sm bg-amber-50 border border-amber-200 text-amber-900 flex items-start gap-2"
        >
          <AlertTriangle className="w-4 h-4 text-amber-700 shrink-0 mt-0.5" aria-hidden="true" />
          <div>
            <p className="font-bold">Atención:</p>
            <p>
              Esta contraseña solo se mostrará una vez. Asegurate de copiarla y enviársela al empleado de forma segura antes de cerrar esta ventana.
            </p>
          </div>
        </div>

        <div className="pt-2">
          <button
            type="button"
            onClick={onClose}
            className="w-full min-h-[44px] py-2 px-4 bg-ee-text hover:opacity-90 text-white text-sm font-medium rounded-ee-sm shadow-sm transition-opacity focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-ee-focus-ring"
          >
            Entendido, ya guardé las credenciales
          </button>
        </div>
      </div>
    </AccessibleModal>
  );
};

export default TemporaryCredentialsModal;
