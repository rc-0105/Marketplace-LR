import { post } from '../../../shared/services/http.js';
import { reservaDesdeApi } from '../models/reserva.js';

const RUTA = '/api/v1/reservas';

/**
 * Confirma la reserva sobre la cotización vigente de la estancia (SOF-33). Si otro huésped ya
 * reservó esas fechas la API responde 409, y si la cotización ya no es vigente (la publicación
 * cambió o un servicio adicional ya no existe), 400. El ErrorApi se propaga.
 * @param {{publicacionId: number, llegada: string, salida: string, serviciosAdicionalesIds?: number[]}} datos
 *   fechas en formato AAAA-MM-DD; serviciosAdicionalesIds son los ids que el huésped agregó
 * @returns {Promise<import('../models/reserva.js').Reserva>}
 */
export async function crearReserva({ publicacionId, llegada, salida, serviciosAdicionalesIds = [] }) {
  return reservaDesdeApi(await post(RUTA, { publicacionId, llegada, salida, serviciosAdicionalesIds }));
}
