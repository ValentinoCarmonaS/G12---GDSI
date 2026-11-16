import { useState, useEffect } from 'react';
import { calculateElapsedMinutes } from '../utils/kdsTimeUtils';

export interface UseKdsCountUpResult {
  elapsedMinutes: number;
  formattedTime: string;
}

/**
 * Hook reactivo para cronómetro progresivo de preparación en minutos (Count-Up).
 * Conforme a RF-011, RF-012 e Invariante 6:
 * - Calcula floor((now - startedAt) / 60).
 * - Expresa el formato como "MM min".
 * - Refresca periódicamente cada 60 segundos (o sincronizado al próximo cambio de minuto)
 *   sin generar peticiones de red adicionales.
 *
 * @param startedAt Timestamp ISO-8601 del instante de inicio fijado por el servidor.
 * @param clockOffsetMs Opcional: desfase en ms entre cliente y servidor.
 */
export function useKdsCountUp(startedAt?: string | null, clockOffsetMs = 0): UseKdsCountUpResult {
  const [elapsedMinutes, setElapsedMinutes] = useState<number>(() => {
    if (!startedAt) return 0;
    return calculateElapsedMinutes(startedAt, clockOffsetMs);
  });

  useEffect(() => {
    if (!startedAt) {
      setElapsedMinutes(0);
      return;
    }

    const updateMinutes = () => {
      setElapsedMinutes(calculateElapsedMinutes(startedAt, clockOffsetMs));
    };

    updateMinutes();

    let intervalId: NodeJS.Timeout | null = null;
    let timeoutId: NodeJS.Timeout | null = null;

    // Cálculo optimizado del desfase hasta el siguiente cambio de minuto exacto
    const startedAtMs = new Date(startedAt).getTime();
    const currentServerTime = Date.now() + clockOffsetMs;
    const elapsedSeconds = Math.max(0, Math.floor((currentServerTime - startedAtMs) / 1000));
    const secondsIntoCurrentMinute = elapsedSeconds % 60;
    const msUntilNextMinute = (60 - secondsIntoCurrentMinute) * 1000;

    timeoutId = setTimeout(() => {
      updateMinutes();
      intervalId = setInterval(updateMinutes, 60000);
    }, msUntilNextMinute);

    return () => {
      if (timeoutId) clearTimeout(timeoutId);
      if (intervalId) clearInterval(intervalId);
    };
  }, [startedAt, clockOffsetMs]);

  return {
    elapsedMinutes,
    formattedTime: `${elapsedMinutes} min`,
  };
}
