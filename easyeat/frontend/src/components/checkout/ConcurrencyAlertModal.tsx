import React from 'react';

export interface UnavailableDishDetail {
  dishId: string;
  dishName: string;
  limitingIngredient?: string;
}

interface ConcurrencyAlertModalProps {
  isOpen: boolean;
  type: 'INSUFFICIENT_STOCK' | 'SLOT_CAPACITY_EXCEEDED';
  message?: string;
  unavailableDishes?: UnavailableDishDetail[];
  onAction: () => void;
  onClose: () => void;
}

export const ConcurrencyAlertModal: React.FC<ConcurrencyAlertModalProps> = ({
  isOpen,
  type,
  message,
  unavailableDishes = [],
  onAction,
  onClose,
}) => {
  if (!isOpen) return null;

  const isStock = type === 'INSUFFICIENT_STOCK';

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm"
    >
      <div className="bg-[#121216] border border-amber-500/30 rounded-2xl max-w-lg w-full p-6 text-[#f0ede8] shadow-2xl space-y-5">
        <div className="flex items-center space-x-3">
          <div className="p-3 bg-amber-500/20 text-amber-400 rounded-xl">
            <svg
              className="w-6 h-6"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="2"
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
              />
            </svg>
          </div>
          <div>
            <h3 className="text-xl font-bold text-[#f0ede8]">
              {isStock ? 'Insumos agotados por concurrencia' : 'Franja horaria saturada'}
            </h3>
            <p className="text-xs text-stone-400">
              Tus productos se mantuvieron guardados en el carrito
            </p>
          </div>
        </div>

        <p className="text-sm text-stone-300 leading-relaxed">
          {message ||
            (isStock
              ? 'Los insumos de uno o más platos se agotaron por alta demanda concurrente antes de consolidar la reserva.'
              : 'La franja horaria seleccionada ha alcanzado su cupo máximo luego de expirar tu reserva de 15 minutos. Por favor, elegí un nuevo horario de retiro.')}
        </p>

        {isStock && unavailableDishes.length > 0 && (
          <div className="bg-[#18181f] border border-stone-800 rounded-xl p-3 space-y-2">
            <span className="text-xs font-semibold text-amber-400 uppercase tracking-wider">
              Platos afectados
            </span>
            <ul className="divide-y divide-stone-800">
              {unavailableDishes.map((dish) => (
                <li key={dish.dishId} className="py-2 flex justify-between items-center text-sm">
                  <span className="font-medium text-stone-200">{dish.dishName}</span>
                  {dish.limitingIngredient && (
                    <span className="text-xs px-2 py-0.5 rounded bg-red-500/20 text-red-300">
                      Falta: {dish.limitingIngredient}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="flex justify-end space-x-3 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm text-stone-400 hover:text-white transition-colors"
          >
            Cerrar
          </button>
          <button
            type="button"
            onClick={onAction}
            className="px-5 py-2.5 text-sm font-semibold rounded-xl bg-[#c9a96e] text-[#0b0b0d] hover:bg-[#d8ba7f] transition-all shadow-md active:scale-95"
          >
            {isStock ? 'Modificar carrito' : 'Elegir otro horario'}
          </button>
        </div>
      </div>
    </div>
  );
};
