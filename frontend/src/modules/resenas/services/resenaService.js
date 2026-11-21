import { post } from '../../../shared/services/http.js';
import { resenaDesdeApi } from '../models/resena.js';

const RUTA_RESERVAS = '/api/v1/reservas';

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
