import React from 'react';
import { AlertCircle, Clock, RefreshCw, X } from 'lucide-react';

export interface ConflictNotificationModalProps {
  isOpen: boolean;
  errorCode: 'SLOT_SOLD_OUT' | 'ACTIVE_RESERVATION_EXISTS' | string;
  errorMessage?: string;
  onClose: () => void;
  onRefreshSlots?: () => void;
  onViewExistingOrder?: () => void;
}

export const ConflictNotificationModal: React.FC<ConflictNotificationModalProps> = ({
  isOpen,
  errorCode,
  errorMessage,
  onClose,
  onRefreshSlots,
  onViewExistingOrder,
}) => {
  if (!isOpen) {
    return null;
  }

  const isSoldOut = errorCode === 'SLOT_SOLD_OUT';
  const isActiveReservation = errorCode === 'ACTIVE_RESERVATION_EXISTS';

  const title = isSoldOut
    ? 'Franja horaria agotada'
    : isActiveReservation
    ? 'Reserva activa en curso'
    : 'Aviso de reserva';

  const defaultDetail = isSoldOut
    ? 'La franja horaria seleccionada acaba de agotarse. Por favor, seleccioná una nueva franja para continuar.'
    : isActiveReservation
    ? 'Ya poseés una orden pendiente de confirmación. Por favor, completá o cancelá tu reserva actual para continuar.'
    : 'Ocurrió un conflicto al procesar tu reserva.';

  const detail = errorMessage || defaultDetail;

  return (
    <div
      data-testid="conflict-notification-modal"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in"
      role="dialog"
      aria-modal="true"
    >
      <div className="relative w-full max-w-md bg-white rounded-2xl shadow-2xl border border-gray-100 overflow-hidden transform transition-all p-6 space-y-6">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-gray-400 hover:text-gray-600 transition-colors p-1 rounded-full hover:bg-gray-100"
          aria-label="Cerrar modal"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-start gap-4">
          <div
            className={`p-3 rounded-full flex-shrink-0 ${
              isSoldOut
                ? 'bg-amber-100 text-amber-600'
                : 'bg-orange-100 text-orange-600'
            }`}
          >
            {isSoldOut ? (
              <Clock className="w-6 h-6" />
            ) : (
              <AlertCircle className="w-6 h-6" />
            )}
          </div>
          <div className="space-y-1">
            <h3 className="text-lg font-bold text-gray-900">{title}</h3>
            <p className="text-sm text-gray-600 leading-relaxed">{detail}</p>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row gap-3 pt-2">
          {isSoldOut && (
            <button
              onClick={() => {
                if (onRefreshSlots) {
                  onRefreshSlots();
                }
                onClose();
              }}
              className="w-full inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold text-white bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 transition-colors shadow-sm"
            >
              <RefreshCw className="w-4 h-4" />
              Elegir otra franja
            </button>
          )}

          {isActiveReservation && (
            <button
              onClick={() => {
                if (onViewExistingOrder) {
                  onViewExistingOrder();
                }
                onClose();
              }}
              className="w-full inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold text-white bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 transition-colors shadow-sm"
            >
              Ver mi reserva
            </button>
          )}

          <button
            onClick={onClose}
            className="w-full inline-flex items-center justify-center px-4 py-2.5 rounded-xl text-sm font-semibold text-gray-700 bg-gray-100 hover:bg-gray-200 transition-colors"
          >
            Cerrar
          </button>
        </div>
      </div>
    </div>
  );
};
