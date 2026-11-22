import { get } from '../../../shared/services/http.js';

const RUTA = '/api/v1/admin/dashboard';

/**
 * Indicadores del negocio (RF-41) sobre el rango de fechas (RF-42). Las fechas vacías no se envían;
 * sin fechas la API calcula sobre todo el histórico. Si la API rechaza el rango (400) el ErrorApi se propaga.
 * @param {{desde?: string, hasta?: string}} [rango] fechas en formato AAAA-MM-DD
 * @returns {Promise<import('../models/dashboard.js').Dashboard>}
 */
export function consultarDashboard(rango = {}) {
  const consulta = new URLSearchParams();
  ['desde', 'hasta'].forEach((nombre) => {
    const valor = rango[nombre];
    if (valor !== undefined && valor !== null && String(valor).trim() !== '') consulta.append(nombre, valor);
  });
  const texto = consulta.toString();
  return get(texto ? `${RUTA}?${texto}` : RUTA);
}
