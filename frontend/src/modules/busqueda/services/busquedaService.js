import { get } from '../../../shared/services/http.js';
import { resultadoDesdeApi } from '../models/resultadoBusqueda.js';

const RUTA = '/api/v1/busqueda';

/**
 * Alojamientos ACTIVOS de la ciudad con disponibilidad entre la llegada y la salida.
 * Si la salida no es posterior a la llegada la API responde 400 y el ErrorApi se propaga.
 * @param {{ciudad: string, llegada: string, salida: string}} criterios fechas en formato AAAA-MM-DD
 * @returns {Promise<import('../models/resultadoBusqueda.js').ResultadoBusqueda[]>}
 */
export async function buscarAlojamientos({ ciudad, llegada, salida }) {
  const consulta = new URLSearchParams({ ciudad, llegada, salida });
  const respuesta = await get(`${RUTA}?${consulta}`);
  return respuesta.map(resultadoDesdeApi);
}
