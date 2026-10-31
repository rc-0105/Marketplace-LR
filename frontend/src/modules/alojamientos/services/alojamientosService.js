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

/**
 * @returns {Promise<import('../models/publicacion.js').OpcionCatalogo[]>} servicios en orden alfabético
 */
export function listarServicios() {
  return get('/api/v1/catalogos/servicios');
}

/**
 * @returns {Promise<import('../models/publicacion.js').PoliticaCancelacion[]>} de la más flexible a la más estricta
 */
export function listarPoliticasCancelacion() {
  return get('/api/v1/catalogos/politicas-cancelacion');
}

/**
 * Reemplaza solo las condiciones de reserva: tarifa de limpieza, modo, política
 * de cancelación y servicios adicionales (RF-20, RF-21, RF-22).
 * @param {number | string} id
 * @param {ReturnType<typeof import('../models/publicacion.js').solicitudConfiguracionDesde>} solicitud
 * @returns {Promise<import('../models/publicacion.js').Publicacion>}
 */
export async function actualizarConfiguracion(id, solicitud) {
  return publicacionDesdeApi(await put(`${RUTA}/${id}/configuracion`, solicitud));
}

/**
 * Reemplaza solo los servicios de la publicación por los ids enviados (RF-18).
 * @param {number | string} id
 * @param {number[]} servicioIds
 * @returns {Promise<import('../models/publicacion.js').Publicacion>}
 */
export async function actualizarServicios(id, servicioIds) {
  return publicacionDesdeApi(await put(`${RUTA}/${id}/servicios`, { servicios: servicioIds }));
}

/**
 * Reemplaza solo las imágenes, en el orden enviado: la primera es la principal (RF-19).
 * @param {number | string} id
 * @param {string[]} urls
 * @returns {Promise<import('../models/publicacion.js').Publicacion>}
 */
export async function actualizarImagenes(id, urls) {
  return publicacionDesdeApi(await put(`${RUTA}/${id}/imagenes`, { imagenes: urls }));
}

/**
 * Pide activar la publicación: un borrador completo queda en revisión y una pausada
 * vuelve a estar activa. Si faltan datos la API responde 422 y el ErrorApi se propaga.
 * @param {number | string} id
 * @returns {Promise<import('../models/publicacion.js').Publicacion>}
 */
export async function activarPublicacion(id) {
  return publicacionDesdeApi(await post(`${RUTA}/${id}/activar`));
}

/**
 * Saca de las búsquedas una publicación activa (409 si no está activa).
 * @param {number | string} id
 * @returns {Promise<import('../models/publicacion.js').Publicacion>}
 */
export async function pausarPublicacion(id) {
  return publicacionDesdeApi(await post(`${RUTA}/${id}/pausar`));
}
