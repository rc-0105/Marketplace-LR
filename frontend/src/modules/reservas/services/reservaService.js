import { post } from '../../../shared/services/http.js';
import { cancelacionDesdeApi, reservaDesdeApi } from '../models/reserva.js';

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

/**
 * Paga la reserva PENDIENTE con una tarjeta de prueba de Stripe (SOF-34). Si la pasarela rechaza el
 * pago la API responde 402 (la reserva queda cancelada); si no respondió a tiempo, 504, y si falló, 502
 * (en ambos la reserva sigue apartada y se puede reintentar). El ErrorApi se propaga.
 * @param {number} reservaId
 * @param {string} metodoPago tarjeta de prueba, por ejemplo 'pm_card_visa'
 * @returns {Promise<import('../models/reserva.js').Reserva>}
 */
export async function pagarReserva(reservaId, metodoPago) {
  return reservaDesdeApi(await post(`${RUTA}/${reservaId}/pago`, { metodoPago }));
}

/**
 * Cancela la reserva CONFIRMADA indicando un motivo (SOF-75, HU-30). La cancela el huésped titular o el
 * anfitrión de la publicación; cualquier otro usuario recibe 403. El valor de devolución es siempre el 100%
 * del total. El ErrorApi se propaga.
 * @param {number} reservaId
 * @param {string} motivo
 * @returns {Promise<import('../models/reserva.js').Cancelacion>}
 */
export async function cancelarReserva(reservaId, motivo) {
  return cancelacionDesdeApi(await post(`${RUTA}/${reservaId}/cancelacion`, { motivo }));
}
