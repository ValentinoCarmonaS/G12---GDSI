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
      className={`inline-flex min-h-[56px] w-full sm:w-auto items-center justify-center gap-2 px-4 py-2 rounded-ee-sm border-2 border-ee-border-strong text-sm font-bold tracking-tight transition-transform duration-150 motion-reduce:transition-none cursor-pointer select-none shadow-ee-elevated active:translate-x-0.5 active:translate-y-0.5 motion-reduce:active:translate-x-0 motion-reduce:active:translate-y-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring disabled:opacity-50 disabled:cursor-not-allowed ${
        isPaused
          ? 'bg-ee-warning-bg text-ee-warning-fg'
          : 'bg-ee-success-bg text-ee-success-fg'
      }`}
    >
      {isLoading ? (
        <Loader2 className="w-5 h-5 animate-spin motion-reduce:animate-none" aria-hidden="true" />
      ) : isPaused ? (
        <PauseCircle className="w-5 h-5 shrink-0" aria-hidden="true" />
      ) : (
        <PlayCircle className="w-5 h-5 shrink-0" aria-hidden="true" />
      )}
      <span>{isPaused ? 'Pausado por cocina' : 'Activo para comensales'}</span>
    </button>
  );
};

export default ChefDishPauseToggle;

