import { get, post } from '../../../shared/services/http.js';
import {
  cancelacionDesdeApi, detalleReservaDesdeApi, reservaDesdeApi, reservaEnRevisionDesdeApi, resumenReservaDesdeApi,
  solicitudReservaDesdeApi,
} from '../models/reserva.js';

const RUTA = '/api/v1/reservas';

/**
 * Reservas del huésped autenticado (SOF-35): las futuras, de la más próxima a la más lejana, y las pasadas,
 * de la más reciente a la más antigua. El ErrorApi se propaga.
 * @returns {Promise<{futuras: import('../models/reserva.js').ResumenReserva[],
 *   pasadas: import('../models/reserva.js').ResumenReserva[]}>}
 */
export async function listarMisReservas() {
  const respuesta = await get(`${RUTA}/mias`);
  return {
    futuras: respuesta.futuras.map(resumenReservaDesdeApi),
    pasadas: respuesta.pasadas.map(resumenReservaDesdeApi),
  };
}

/**
 * Una reserva del huésped con su alojamiento, desglose y pago (SOF-35). Si no existe la API responde 404 y si
 * es de otro usuario, 403. El ErrorApi se propaga.
 * @param {number|string} reservaId
 * @returns {Promise<import('../models/reserva.js').DetalleReserva>}
 */
export async function consultarReserva(reservaId) {
  return detalleReservaDesdeApi(await get(`${RUTA}/${reservaId}`));
}

/**
 * Confirma la reserva sobre la cotización vigente de la estancia (SOF-33). Si otro huésped ya
 * reservó esas fechas la API responde 409, y si la cotización ya no es vigente (la publicación
 * cambió o un servicio adicional ya no existe), 400. El ErrorApi se propaga.
 *
 * La clave de idempotencia (SOF-77) identifica el intento: reintentar con la misma clave devuelve la
 * misma reserva en lugar de crear otra. Quien reintenta debe pasar la clave del primer intento.
 * @param {{publicacionId: number, llegada: string, salida: string, serviciosAdicionalesIds?: number[]}} datos
 *   fechas en formato AAAA-MM-DD; serviciosAdicionalesIds son los ids que el huésped agregó
 * @param {string} [claveIdempotencia] UUID del intento; si falta se genera uno nuevo
 * @returns {Promise<import('../models/reserva.js').Reserva>}
 */
export async function crearReserva({ publicacionId, llegada, salida, serviciosAdicionalesIds = [] },
  claveIdempotencia = crypto.randomUUID()) {
  return reservaDesdeApi(await post(RUTA, { publicacionId, llegada, salida, serviciosAdicionalesIds },
    { 'Idempotency-Key': claveIdempotencia }));
}

/**
 * Paga la reserva PENDIENTE con una tarjeta de prueba de Stripe (SOF-34). Si la pasarela rechaza el
 * pago la API responde 402 (la reserva queda cancelada); si no respondió a tiempo, 504, y si falló, 502
 * (en ambos la reserva sigue apartada y se puede reintentar). El ErrorApi se propaga.
 * @param {number} reservaId
 * @param {string} metodoPago tarjeta de prueba, por ejemplo 'pm_card_visa'
 * @param {string} [clave] UUID del intento; reutilizarlo si se reintenta el mismo pago
 * @returns {Promise<import('../models/reserva.js').Reserva>}
 */
export async function pagarReserva(reservaId, metodoPago, clave = crypto.randomUUID()) {
  return reservaDesdeApi(await post(`${RUTA}/${reservaId}/pago`, { metodoPago }, { 'Idempotency-Key': clave }));
}

export function anticiparCancelacion(reservaId) {
  return get(`${RUTA}/${reservaId}/cancelacion/previsualizacion`);
}

/**
 * Cancela la reserva CONFIRMADA y devuelve el resultado de la solicitud de reembolso.
 * @param {number} reservaId
 * @param {string} motivo
 * @returns {Promise<import('../models/reserva.js').Cancelacion>}
 */
export async function cancelarReserva(reservaId, motivo) {
  return cancelacionDesdeApi(await post(`${RUTA}/${reservaId}/cancelacion`, { motivo }));
}

/**
 * Solicitudes de reserva que esperan la respuesta del anfitrión autenticado (SOF-79), la más urgente primero.
 * @returns {Promise<import('../models/reserva.js').SolicitudReserva[]>}
 */
export async function listarSolicitudes() {
  const respuesta = await get(`${RUTA}/solicitudes`);
  return respuesta.map(solicitudReservaDesdeApi);
}

/**
 * Aprueba una solicitud: la reserva pasa a PENDIENTE y el huésped tiene 24 h para pagar (SOF-79). 409 si ya
 * no se puede responder (vencida, ya resuelta o publicación bloqueada). El ErrorApi se propaga.
 * @param {number} reservaId
 * @returns {Promise<{reservaId: number, estado: string, venceEn: string}>}
 */
export function aprobarSolicitud(reservaId) {
  return post(`${RUTA}/${reservaId}/aprobar`);
}

/**
 * Rechaza una solicitud con un motivo obligatorio de hasta 500 caracteres (SOF-79). 400 si el motivo es
 * inválido y 409 si ya no se puede responder. El ErrorApi se propaga.
 * @param {number} reservaId
 * @param {string} motivo
 * @returns {Promise<{reservaId: number, estado: string, motivo: string}>}
 */
export function rechazarSolicitud(reservaId, motivo) {
  return post(`${RUTA}/${reservaId}/rechazar`, { motivo: motivo.trim() });
}

const RUTA_REVISION = '/api/v1/admin/reservas';

/**
 * Reservas en revisión manual porque su publicación fue bloqueada (SOF-83), para el administrador.
 * @returns {Promise<import('../models/reserva.js').ReservaEnRevision[]>}
 */
export async function listarReservasEnRevision() {
  const respuesta = await get(`${RUTA_REVISION}/revision`);
  return respuesta.map(reservaEnRevisionDesdeApi);
}

/**
 * Resuelve una reserva en revisión manual (SOF-83): CONFIRMAR la mantiene; CANCELAR la cancela con la
 * devolución total y exige motivo. 409 si ya no está en revisión; 502 si la pasarela no procesó la devolución
 * (la reserva sigue en revisión). El ErrorApi se propaga.
 * @param {number} reservaId
 * @param {'CONFIRMAR' | 'CANCELAR'} decision
 * @param {string} [motivo] obligatorio para CANCELAR
 */
export function resolverReservaEnRevision(reservaId, decision, motivo) {
  const cuerpo = decision === 'CANCELAR' ? { decision, motivo } : { decision };
  return post(`${RUTA_REVISION}/${reservaId}/resolver`, cuerpo);
}
