import { get } from '../../../shared/services/http.js';
import { resultadoDesdeApi } from '../models/resultadoBusqueda.js';

const RUTA = '/api/v1/busqueda';

const FILTROS_OPCIONALES = ['capacidad', 'tipo', 'precioMin', 'precioMax'];

/**
 * Alojamientos ACTIVOS de la ciudad con disponibilidad entre la llegada y la salida.
 * Los filtros son opcionales: los vacíos no se envían y los que vienen se combinan.
 * Si la salida no es posterior a la llegada, o el precio mínimo supera al máximo, la API
 * responde 400 y el ErrorApi se propaga.
 * @param {{ciudad: string, llegada: string, salida: string, capacidad?: string|number, tipo?: string,
 *   precioMin?: string|number, precioMax?: string|number, servicios?: number[]}} criterios
 *   fechas en formato AAAA-MM-DD; tipo es el nombre del tipo; servicios son ids del catálogo
 * @returns {Promise<import('../models/resultadoBusqueda.js').ResultadoBusqueda[]>}
 */
export async function buscarAlojamientos({ ciudad, llegada, salida, servicios = [], ...filtros }) {
  const consulta = new URLSearchParams({ ciudad, llegada, salida });
  FILTROS_OPCIONALES.forEach((nombre) => {
    const valor = filtros[nombre];
    if (valor !== undefined && valor !== null && String(valor).trim() !== '') consulta.append(nombre, valor);
  });
  servicios.forEach((id) => consulta.append('servicios', id));
  const respuesta = await get(`${RUTA}?${consulta}`);
  return respuesta.map(resultadoDesdeApi);
}
