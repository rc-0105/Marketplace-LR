import { get } from '../../../shared/services/http.js';
import { publicacionDesdeApi } from '../models/publicacion.js';

const RUTA = '/api/v1/publicaciones';

/**
 * Publicaciones del anfitrión con sesión, la más reciente primero.
 * @returns {Promise<import('../models/publicacion.js').Publicacion[]>}
 */
export async function listarMisPublicaciones() {
  const respuesta = await get(`${RUTA}/mias`);
  return respuesta.map(publicacionDesdeApi);
}
