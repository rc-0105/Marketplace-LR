/**
 * @typedef {object} NotificacionApi
 * @property {number} id
 * @property {'RESERVA_CONFIRMADA'|'RESERVA_RECHAZADA'|'RESERVA_CANCELADA'|'PUBLICACION_ESTADO'|'CUENTA_RESTRINGIDA'|'SOLICITUD_RECIBIDA'|'SOLICITUD_APROBADA'|'SOLICITUD_RECHAZADA'|'SOLICITUD_VENCIDA'} tipo
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

const TIPOS_DE_SOLICITUD_PARA_HUESPED = ['SOLICITUD_APROBADA', 'SOLICITUD_RECHAZADA', 'SOLICITUD_VENCIDA'];

/**
 * A dónde lleva una notificación de solicitud de reserva (SOF-79): la nueva solicitud, al anfitrión a sus
 * solicitudes; la respuesta o el vencimiento, al huésped al detalle de su reserva. Otros tipos no enlazan.
 * @param {Pick<NotificacionApi, 'tipo'|'entidadId'>} notificacion
 * @returns {{ruta: string, texto: string}|null}
 */
export function enlaceDeNotificacion({ tipo, entidadId }) {
  if (tipo === 'SOLICITUD_RECIBIDA') return { ruta: '/anfitrion/solicitudes', texto: 'Ver solicitudes' };
  if (TIPOS_DE_SOLICITUD_PARA_HUESPED.includes(tipo) && entidadId != null) {
    return { ruta: `/huesped/reservas/${entidadId}`, texto: 'Ver reserva' };
  }
  return null;
}

/**
 * @param {NotificacionApi} notificacion
 * @returns {NotificacionVista}
 */
export function aVistaNotificacion({ id, tipo, mensaje, leida, creadaEn }) {
  return { id, tipo, mensaje, leida: Boolean(leida), creadaEn, fecha: formatearFecha(creadaEn) };
}
