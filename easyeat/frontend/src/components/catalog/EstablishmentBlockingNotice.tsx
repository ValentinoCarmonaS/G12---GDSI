import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Store, AlertOctagon, ArrowLeft } from 'lucide-react';

export interface EstablishmentBlockingNoticeProps {
  establishmentName?: string;
  status?: string;
  onBackToSearch?: () => void;
}

export const EstablishmentBlockingNotice: React.FC<EstablishmentBlockingNoticeProps> = ({
  establishmentName,
  status,
  onBackToSearch,
}) => {
  const navigate = useNavigate();

  const handleBack = () => {
    if (onBackToSearch) {
      onBackToSearch();
    } else {
      navigate('/descubrimiento');
    }
  };

  return (
    <div
      role="region"
      aria-label="Aviso de inactividad de establecimiento"
      className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 px-4 py-12"
    >
      <div className="max-w-md w-full bg-white dark:bg-gray-800 rounded-3xl p-8 shadow-xl border border-gray-100 dark:border-gray-700 text-center">
        {/* Icon Header */}
        <div className="relative mx-auto w-20 h-20 mb-6 flex items-center justify-center bg-rose-50 dark:bg-rose-950/40 rounded-3xl">
          <Store className="w-10 h-10 text-rose-500" />
          <div className="absolute -bottom-1 -right-1 p-1.5 bg-rose-600 text-white rounded-full shadow-md">
            <AlertOctagon className="w-4 h-4" />
          </div>
        </div>

        {/* Title */}
        <h2 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">
          Establecimiento inactivo
        </h2>

        {establishmentName && (
          <p className="text-base font-semibold text-amber-600 dark:text-amber-400 mb-3">
            {establishmentName}
          </p>
        )}

        {/* Message */}
        <p className="text-sm text-gray-600 dark:text-gray-300 leading-relaxed mb-8">
          Este establecimiento se encuentra temporalmente inactivo y no está recibiendo pedidos
          en este momento.
          {status && (
            <span className="block mt-1 text-xs text-gray-400 dark:text-gray-500 uppercase tracking-wider">
              Estado: {status}
            </span>
          )}
        </p>

        {/* CTA */}
        <button
          type="button"
          onClick={handleBack}
          className="w-full inline-flex items-center justify-center gap-2 px-6 py-3.5 bg-amber-600 hover:bg-amber-700 text-white font-medium rounded-2xl shadow-md shadow-amber-500/20 transition-all cursor-pointer"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Volver a buscar restaurantes</span>
        </button>
      </div>
    </div>
  );
};
