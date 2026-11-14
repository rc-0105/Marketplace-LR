import { post } from '../../../shared/services/http.js';
import { cotizacionDesdeApi } from '../models/cotizacion.js';

const RUTA = '/api/v1/cotizaciones';

/**
 * Calcula el desglose de una estancia sin reservarla. Si la salida no es posterior a la llegada,
 * o un servicio adicional no es de la publicación, la API responde 400; si la publicación no está
 * activa, 404. El ErrorApi se propaga.
 * @param {{publicacionId: number, llegada: string, salida: string, serviciosAdicionales?: number[]}} datos
 *   fechas en formato AAAA-MM-DD; serviciosAdicionales son los ids que el huésped agregó
 * @returns {Promise<import('../models/cotizacion.js').Cotizacion>}
 */
export async function cotizarEstancia({ publicacionId, llegada, salida, serviciosAdicionales = [] }) {
  return cotizacionDesdeApi(await post(RUTA, { publicacionId, llegada, salida, serviciosAdicionales }));
}
