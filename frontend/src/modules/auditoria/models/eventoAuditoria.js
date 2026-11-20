/**
 * @typedef {object} EventoAuditoria
 * @property {number} id
 * @property {string} fecha instante ISO con zona
 * @property {string|null} usuario
 * @property {string} accion valor de AccionCritica
 * @property {string} entidad
 * @property {string|null} entidadId
 * @property {'EXITO'|'FALLO'} resultado
 * @property {Record<string, unknown>} datos
 * @property {string|null} correlacion
 */

/** Valores de AccionCritica que acepta el filtro `evento` de la API. */
export const ACCIONES_AUDITORIA = [
  'AUTENTICACION', 'PUBLICACION', 'RESERVA', 'CANCELACION', 'RESENA', 'ACCION_ADMINISTRATIVA',
];

const FORMATO_BOGOTA = new Intl.DateTimeFormat('es-CO', {
  timeZone: 'America/Bogota',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

/**
 * Muestra un instante en hora de Bogotá como `dd/mm/aaaa hh:mm:ss`.
 * @param {string|null|undefined} instante
 * @returns {string} vacío si no hay fecha; el texto original si no se puede interpretar
 */
export function formatearFechaBogota(instante) {
  if (!instante) return '';
  const fecha = new Date(instante);
  if (Number.isNaN(fecha.getTime())) return String(instante);
  const partes = Object.fromEntries(FORMATO_BOGOTA.formatToParts(fecha).map(({ type, value }) => [type, value]));
  return `${partes.day}/${partes.month}/${partes.year} ${partes.hour}:${partes.minute}:${partes.second}`;
}

/**
 * @param {Record<string, unknown>|null|undefined} datos
 * @returns {string} JSON indentado para mostrar
 */
export function formatearDatos(datos) {
  return JSON.stringify(datos ?? {}, null, 2);
}
