import { eliminar, get, post, put } from '../../../shared/services/http.js';
import { calendarioDesdeApi } from '../models/calendario.js';
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

/**
 * Calendario de la publicación en la ventana [desde, hasta); `hasta` no entra. Sin parámetros, la API
 * entrega los próximos 12 meses. Responde 404 si la publicación no existe o no es visible.
 * @param {number | string} publicacionId
 * @param {{desde?: string, hasta?: string}} [ventana] fechas AAAA-MM-DD
 * @returns {Promise<import('../models/calendario.js').Calendario>}
 */
export async function consultarCalendario(publicacionId, { desde, hasta } = {}) {
  const consulta = new URLSearchParams();
  if (desde) consulta.set('desde', desde);
  if (hasta) consulta.set('hasta', hasta);
  const texto = consulta.toString();
  const ruta = `/api/v1/publicaciones/${publicacionId}/calendario`;
  return calendarioDesdeApi(await get(texto ? `${ruta}?${texto}` : ruta));
}
