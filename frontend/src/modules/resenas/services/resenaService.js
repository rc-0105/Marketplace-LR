import { get, post } from '../../../shared/services/http.js';
import { resenaDesdeApi, resenasParaModerarDesdeApi, resenasPublicacionDesdeApi } from '../models/resena.js';

const RUTA_RESERVAS = '/api/v1/reservas';
const RUTA_PUBLICACIONES = '/api/v1/publicaciones';
const RUTA_ADMIN_RESENAS = '/api/v1/admin/resenas';

/**
 * Publica la reseña de una reserva COMPLETADA del huésped autenticado (SOF-80, HU-35). La API responde 409 si
 * la estancia aún no terminó o la reserva ya tiene su reseña, 403 si la reserva es de otro huésped y 404 si no
 * existe. El ErrorApi se propaga.
 * @param {number} reservaId
 * @param {{calificacion: number, contenido: string}} datos calificación de 1 a 5 y comentario
 * @returns {Promise<import('../models/resena.js').Resena>}
 */
export async function crearResena(reservaId, { calificacion, contenido }) {
  return resenaDesdeApi(await post(`${RUTA_RESERVAS}/${reservaId}/resena`, { calificacion, contenido }));
}

/**
 * Las reseñas visibles de una publicación, de la más reciente a la más antigua, con su calificación promedio
 * (SOF-81, HU-36). Sin reseñas el promedio es nulo y la lista queda vacía. El ErrorApi se propaga.
 * @param {number | string} publicacionId
 * @returns {Promise<import('../models/resena.js').ResenasPublicacion>}
 */
export async function consultarResenas(publicacionId) {
  return resenasPublicacionDesdeApi(await get(`${RUTA_PUBLICACIONES}/${publicacionId}/resenas`));
}

/**
 * Las reseñas visibles de toda la plataforma, de la más reciente a la más antigua, para que el administrador
 * las modere (SOF-82, HU-37). El ErrorApi se propaga.
 * @returns {Promise<import('../models/resena.js').ResenaParaModerar[]>}
 */
export async function listarResenasParaModerar() {
  return resenasParaModerarDesdeApi(await get(RUTA_ADMIN_RESENAS));
}

/**
 * Oculta una reseña con el motivo del administrador (SOF-82, HU-37). La API responde 409 si ya estaba oculta y
 * 404 si no existe. El ErrorApi se propaga.
 * @param {number} resenaId
 * @param {string} motivo
 * @returns {Promise<{id: number, estado: string, motivo: string}>}
 */
export function ocultarResena(resenaId, motivo) {
  return post(`${RUTA_ADMIN_RESENAS}/${resenaId}/ocultar`, { motivo });
}
