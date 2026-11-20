import { get } from '../../../shared/services/http.js';

// Módulo de solo lectura (RF-59): este cliente solo consulta, no define otra llamada que GET.
const RUTA = '/api/v1/admin/auditoria';
const FILTROS = ['usuario', 'evento', 'entidad', 'entidadId', 'desde', 'hasta'];

const TAMANO_POR_DEFECTO = 50;

/**
 * Eventos de auditoría, del más reciente al más antiguo. Los filtros vacíos no se envían y los
 * informados se combinan. Si la API rechaza un parámetro (400) el ErrorApi se propaga.
 * @param {{usuario?: string, evento?: string, entidad?: string, entidadId?: string, desde?: string,
 *   hasta?: string}} filtros fechas en formato AAAA-MM-DD
 * @param {number} [pagina] base 0
 * @param {number} [tamano]
 * @returns {Promise<{contenido: import('../models/eventoAuditoria.js').EventoAuditoria[], pagina: number,
 *   tamano: number, totalElementos: number, totalPaginas: number}>}
 */
export function listar(filtros, pagina = 0, tamano = TAMANO_POR_DEFECTO) {
  const consulta = new URLSearchParams();
  FILTROS.forEach((nombre) => {
    const valor = filtros[nombre];
    if (valor !== undefined && valor !== null && String(valor).trim() !== '') consulta.append(nombre, valor);
  });
  consulta.append('pagina', pagina);
  consulta.append('tamano', tamano);
  return get(`${RUTA}?${consulta}`);
}

/**
 * Un evento de auditoría con todos sus campos. Si no existe la API responde 404 y el ErrorApi se propaga.
 * @param {number|string} id
 * @returns {Promise<import('../models/eventoAuditoria.js').EventoAuditoria>}
 */
export function detalle(id) {
  return get(`${RUTA}/${id}`);
}
