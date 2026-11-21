import { get } from '../../../shared/services/http.js';
import { resultadoDesdeApi } from '../models/resultadoBusqueda.js';

const RUTA = '/api/v1/inicio/destacados';

/**
 * Alojamientos ACTIVOS más recientes para la vitrina del Inicio; la ruta es pública, no pide sesión.
 * @param {number} [limite] cantidad máxima; la API lo ajusta a su rango permitido
 * @returns {Promise<import('../models/resultadoBusqueda.js').ResultadoBusqueda[]>}
 */
export async function consultarDestacados(limite) {
  const consulta = limite ? `?limite=${limite}` : '';
  const respuesta = await get(`${RUTA}${consulta}`);
  return respuesta.map(resultadoDesdeApi);
}
