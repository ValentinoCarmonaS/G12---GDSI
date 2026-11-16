/**
 * Utilitarios de cálculo temporal y formateo para KDS (Kitchen Display System).
 */

/**
 * Calcula los minutos transcurridos desde que se inició la preparación de una comanda (startedAt).
 * Aplica floor((now - startedAt) / 60) garantizando que no sea negativo.
 * Soporta corrección de desfase de reloj (clockOffsetMs) contra el reloj del servidor.
 *
 * @param startedAtIso Timestamp ISO-8601 UTC en que el chef pulsó "EMPEZAR".
 * @param clockOffsetMs Desfase en milisegundos entre el cliente y el servidor (serverTime - clientTime).
 * @returns Minutos enteros transcurridos (>= 0).
 */
export function calculateElapsedMinutes(startedAtIso: string, clockOffsetMs = 0): number {
  const startedAt = new Date(startedAtIso).getTime();
  const currentServerTime = Date.now() + clockOffsetMs;
  const elapsedSeconds = Math.max(0, Math.floor((currentServerTime - startedAt) / 1000));
  return Math.floor(elapsedSeconds / 60);
}

/**
 * Determina si una comanda en preparación ha superado el umbral de alerta por demora excesiva.
 * Según RF-014 e Invariante 7: umbral de 15 minutos exactos (tiempo >= 15 min).
 *
 * @param elapsedMinutes Minutos enteros transcurridos.
 * @returns true si elapsedMinutes >= 15, false en caso contrario.
 */
export function isTicketDelayed(elapsedMinutes: number): boolean {
  return elapsedMinutes >= 15;
}

/**
 * Formatea un ítem de plato para la tarjeta expandida de la Columna 2 en el tablero KDS.
 * Según RF-009 y formato unificado: "[Cantidad]x [Nombre del Plato]".
 *
 * @param quantity Cantidad de porciones/unidades del plato.
 * @param name Nombre comercial del plato.
 * @returns Cadena con formato "[Cantidad]x [Nombre]".
 */
export function formatDishItem(quantity: number, name: string): string {
  const cleanName = (name ?? '').trim();
  return `${quantity}x ${cleanName}`;
}
