import { get, post, put } from '../../../shared/services/http.js';
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

/**
 * @param {number | string} id
 * @returns {Promise<import('../models/publicacion.js').Publicacion>}
 */
export async function consultarPublicacion(id) {
  return publicacionDesdeApi(await get(`${RUTA}/${id}`));
}

/**
 * Crea la publicación en borrador; si la API la rechaza (400) el ErrorApi se propaga.
 * @param {ReturnType<typeof import('../models/publicacion.js').solicitudDesde>} solicitud
 * @returns {Promise<import('../models/publicacion.js').Publicacion>}
 */
export async function crearPublicacion(solicitud) {
  return publicacionDesdeApi(await post(RUTA, solicitud));
}

/**
 * Reemplaza los datos de la publicación (403 si no es del anfitrión, 404 si no existe).
 * @param {number | string} id
 * @param {ReturnType<typeof import('../models/publicacion.js').solicitudDesde>} solicitud
 * @returns {Promise<import('../models/publicacion.js').Publicacion>}
 */
export async function editarPublicacion(id, solicitud) {
  return publicacionDesdeApi(await put(`${RUTA}/${id}`, solicitud));
}

/**
 * @returns {Promise<import('../models/publicacion.js').OpcionCatalogo[]>} tipos en orden alfabético
 */
export function listarTiposAlojamiento() {
  return get('/api/v1/catalogos/tipos-alojamiento');
}
