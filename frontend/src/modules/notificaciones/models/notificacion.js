/**
 * @typedef {object} NotificacionApi
 * @property {number} id
 * @property {'RESERVA_CONFIRMADA'|'RESERVA_RECHAZADA'|'RESERVA_CANCELADA'|'PUBLICACION_ESTADO'|'CUENTA_RESTRINGIDA'} tipo
 * @property {string} mensaje
 * @property {string} entidad
 * @property {number|null} entidadId
 * @property {boolean} leida
 * @property {string} creadaEn instante ISO con zona
 */

/**
 * @typedef {object} NotificacionVista
 * @property {number} id
 * @property {string} tipo
 * @property {string} mensaje
 * @property {boolean} leida
 * @property {string} creadaEn
 * @property {string} fecha `dd/mm/aaaa hh:mm` en hora de Bogotá
 */

const FORMATO_BOGOTA = new Intl.DateTimeFormat('es-CO', {
  timeZone: 'America/Bogota',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

function formatearFecha(instante) {
  if (!instante) return '';
  const fecha = new Date(instante);
  if (Number.isNaN(fecha.getTime())) return String(instante);
  const partes = Object.fromEntries(FORMATO_BOGOTA.formatToParts(fecha).map(({ type, value }) => [type, value]));
  return `${partes.day}/${partes.month}/${partes.year} ${partes.hour}:${partes.minute}`;
}

/**
 * @param {NotificacionApi} notificacion
 * @returns {NotificacionVista}
 */
export function aVistaNotificacion({ id, tipo, mensaje, leida, creadaEn }) {
  return { id, tipo, mensaje, leida: Boolean(leida), creadaEn, fecha: formatearFecha(creadaEn) };
}
