/**
 * Utilitarios de formateo específicos para KDS (Kitchen Display System).
 */

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
  return cleanName ? `${quantity}x ${cleanName}` : `${quantity}x`;
}
