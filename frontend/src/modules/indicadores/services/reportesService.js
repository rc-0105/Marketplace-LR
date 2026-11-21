import { descargar, get } from '../../../shared/services/http.js';

const RUTA = '/api/v1/admin/reportes';

/**
 * Parámetros de la consulta con las fechas que tienen valor (RF-42).
 * @param {{desde?: string, hasta?: string}} rango
 * @param {Record<string, string>} [extra] parámetros que van antes de las fechas
 */
function consultaDe(rango, extra = {}) {
  const consulta = new URLSearchParams(extra);
  ['desde', 'hasta'].forEach((nombre) => {
    const valor = rango[nombre];
    if (valor !== undefined && valor !== null && String(valor).trim() !== '') consulta.append(nombre, valor);
  });
  const texto = consulta.toString();
  return texto ? `?${texto}` : '';
}

/**
 * Un reporte tal como se muestra en pantalla (RF-43): encabezados y filas, una celda por encabezado.
 * Si la API rechaza el rango (400) el ErrorApi se propaga.
 * @param {string} tipo usuarios, publicaciones, reservas, ingresos o demanda
 * @param {{desde?: string, hasta?: string}} [rango] fechas en formato AAAA-MM-DD
 */
export function consultarReporte(tipo, rango = {}) {
  return get(`${RUTA}/${tipo}${consultaDe(rango)}`);
}

/**
 * El mismo reporte como archivo (RF-43). La API registra quién lo exportó y con qué filtros (RF-44).
 * @param {string} tipo
 * @param {'pdf' | 'xlsx'} formato
 * @param {{desde?: string, hasta?: string}} [rango]
 * @returns {Promise<{contenido: Blob, nombre: string}>}
 */
export function exportarReporte(tipo, formato, rango = {}) {
  return descargar(`${RUTA}/${tipo}${consultaDe(rango, { formato })}`);
}

/** Entrega al navegador un archivo descargado para que lo guarde con su nombre. */
export function guardarArchivo({ contenido, nombre }) {
  const url = URL.createObjectURL(contenido);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  enlace.click();
  URL.revokeObjectURL(url);
}
