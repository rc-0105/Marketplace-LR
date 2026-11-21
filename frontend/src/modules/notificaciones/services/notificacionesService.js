import { get, post } from '../../../shared/services/http.js';

const RUTA = '/api/v1/notificaciones';

/**
 * Las 50 notificaciones más recientes del usuario de la sesión, de la más nueva a la más vieja,
 * con el total de no leídas. Si la API falla el ErrorApi se propaga.
 * @returns {Promise<{noLeidas: number,
 *   notificaciones: import('../models/notificacion.js').NotificacionApi[]}>}
 */
export function listarNotificaciones() {
  return get(RUTA);
}

/**
 * Marca como leída una notificación propia (idempotente). Una ajena o inexistente responde 404.
 * @param {number|string} id
 * @returns {Promise<import('../models/notificacion.js').NotificacionApi>}
 */
export function marcarLeida(id) {
  return post(`${RUTA}/${id}/leer`);
}
