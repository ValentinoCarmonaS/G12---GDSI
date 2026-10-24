import React, { useState } from 'react';

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

  if (!isOpen) return null;

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
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-fade-in"
    >
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6 space-y-5 border border-gray-100">
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center text-blue-600 font-bold">
            🔑
          </div>
          <div>
            <h3 id="modal-title" className="text-lg font-bold text-gray-900">
              Credencial temporal generada
            </h3>
            <p className="text-xs text-gray-500">Nuevo miembro del equipo</p>
          </div>
        </div>

        <div className="bg-gray-50 rounded-md p-3 text-sm space-y-1">
          <p className="text-gray-700">
            <span className="font-semibold text-gray-900">Empleado: </span>
            {staffName}
          </p>
          <p className="text-gray-700">
            <span className="font-semibold text-gray-900">Email: </span>
            {email}
          </p>
        </div>

        <div>
          <label className="block text-xs font-semibold text-gray-700 mb-1">
            Contraseña temporal (12 caracteres)
          </label>
          <div className="flex items-center space-x-2">
            <div className="flex-1 bg-gray-100 border border-gray-300 rounded px-3 py-2 font-mono text-sm tracking-wide text-gray-800 select-all overflow-x-auto">
              {temporaryCredential}
            </div>
            <button
              type="button"
              onClick={handleCopy}
              className={`px-3 py-2 text-xs font-medium rounded transition-colors ${
                copied
                  ? 'bg-green-600 text-white'
                  : 'bg-blue-600 text-white hover:bg-blue-700'
              }`}
            >
              {copied ? '¡Copiado!' : 'Copiar'}
            </button>
          </div>
        </div>

        <div
          role="alert"
          className="p-3 text-xs rounded-md bg-amber-50 border border-amber-200 text-amber-800 space-y-1"
        >
          <p className="font-bold">⚠️ Atención:</p>
          <p>
            Esta contraseña solo se mostrará una vez. Asegurate de copiarla y enviársela al empleado de forma segura antes de cerrar esta ventana.
          </p>
        </div>

        <div className="pt-2">
          <button
            type="button"
            onClick={onClose}
            className="w-full py-2 px-4 bg-gray-800 hover:bg-gray-900 text-white text-sm font-medium rounded-md shadow-sm transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-700"
          >
            Entendido, ya guardé las credenciales
          </button>
        </div>
      </div>
    </div>
  );
};

export default TemporaryCredentialsModal;
