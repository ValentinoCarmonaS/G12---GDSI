import React, { useState } from 'react';
import { PauseCircle, PlayCircle, Loader2 } from 'lucide-react';

export interface ChefDishPauseToggleProps {
  dishId: string;
  initialPaused: boolean;
  onToggle: (dishId: string, paused: boolean) => Promise<unknown>;
  disabled?: boolean;
}

export const ChefDishPauseToggle: React.FC<ChefDishPauseToggleProps> = ({
  dishId,
  initialPaused,
  onToggle,
  disabled = false,
}) => {
  const [isPaused, setIsPaused] = useState<boolean>(initialPaused);
  const [isLoading, setIsLoading] = useState<boolean>(false);

  // Sync with prop if it changes externally (e.g. from polling sync)
  React.useEffect(() => {
    setIsPaused(initialPaused);
  }, [initialPaused]);

  const handleToggle = async () => {
    if (disabled || isLoading) return;

    const nextState = !isPaused;
    // Optimistic UI update
    setIsPaused(nextState);
    setIsLoading(true);

    try {
      await onToggle(dishId, nextState);
    } catch {
      // Rollback upon failure
      setIsPaused(!nextState);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <button
      type="button"
      role="switch"
      aria-checked={isPaused}
      aria-label={isPaused ? 'Despausar plato (reincorporar al catálogo)' : 'Pausar plato (ocultar del catálogo)'}
      onClick={handleToggle}
      disabled={disabled || isLoading}
      className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold transition-all shadow-xs cursor-pointer select-none active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed ${
        isPaused
          ? 'bg-amber-100 text-amber-900 border border-amber-300 hover:bg-amber-200'
          : 'bg-emerald-100 text-emerald-900 border border-emerald-300 hover:bg-emerald-200'
      }`}
    >
      {isLoading ? (
        <Loader2 className="w-4 h-4 animate-spin text-gray-600" />
      ) : isPaused ? (
        <PauseCircle className="w-4 h-4 text-amber-700" />
      ) : (
        <PlayCircle className="w-4 h-4 text-emerald-700" />
      )}
      <span>{isPaused ? 'Pausado por cocina' : 'Activo para comensales'}</span>
    </button>
  );
};

export default ChefDishPauseToggle;
