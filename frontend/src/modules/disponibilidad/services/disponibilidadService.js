import { eliminar, get, post, put } from '../../../shared/services/http.js';
import { periodoDesdeApi } from '../models/periodo.js';

/**
 * Periodos de la publicación del anfitrión, del más próximo al más lejano.
 * @param {number | string} publicacionId
 * @returns {Promise<import('../models/periodo.js').Periodo[]>}
 */
export async function listarPeriodos(publicacionId) {
  const respuesta = await get(`/api/v1/publicaciones/${publicacionId}/disponibilidad`);
  return respuesta.map(periodoDesdeApi);
}

/**
 * Registra un periodo; la API responde 400 si las fechas no son válidas y 409 si se cruza con otro.
 * @param {number | string} publicacionId
 * @param {{fechaInicio: string, fechaFin: string}} fechas
 * @returns {Promise<import('../models/periodo.js').Periodo>}
 */
export async function registrarPeriodo(publicacionId, fechas) {
  return periodoDesdeApi(await post(`/api/v1/publicaciones/${publicacionId}/disponibilidad`, fechas));
}

/**
 * Cambia las fechas; la API responde 409 si una reserva confirmada ocupa el periodo.
 * @param {number} periodoId
 * @param {{fechaInicio: string, fechaFin: string}} fechas
 * @returns {Promise<import('../models/periodo.js').Periodo>}
 */
export async function modificarPeriodo(periodoId, fechas) {
  return periodoDesdeApi(await put(`/api/v1/disponibilidad/${periodoId}`, fechas));
}

/**
 * @param {number} periodoId
 * @returns {Promise<void>}
 */
export async function eliminarPeriodo(periodoId) {
  await eliminar(`/api/v1/disponibilidad/${periodoId}`);
}
