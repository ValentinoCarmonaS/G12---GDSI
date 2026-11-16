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

    // Actualización inmediata por si cambió el prop
    setElapsedMinutes(calculateElapsedMinutes(startedAt, clockOffsetMs));

    // Intervalo regular de 1000ms o 60000ms: usando 10000ms o 60000ms.
    // Un intervalo de 10s o 60s garantiza actualizar en el cambio de minuto
    // respetando estrictamente el refresco periódico y sin consumo de red.
    const interval = setInterval(() => {
      setElapsedMinutes(calculateElapsedMinutes(startedAt, clockOffsetMs));
    }, 1000);

    return () => clearInterval(interval);
  }, [startedAt, clockOffsetMs]);

  return {
    elapsedMinutes,
    formattedTime: `${elapsedMinutes} min`,
  };
}
