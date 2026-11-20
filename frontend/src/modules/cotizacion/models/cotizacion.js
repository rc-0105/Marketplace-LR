/**
 * Desglose del precio de una estancia (RF-32, RF-33).
 * @typedef {Object} Cotizacion
 * @property {number} noches
 * @property {number} precioNoche
 * @property {number} precioBase
 * @property {number} tarifaLimpieza
 * @property {number} tarifaServicio
 * @property {number} descuento
 * @property {{id: number, nombre: string, costo: number}[]} serviciosAdicionales una línea por servicio
 * @property {number} totalServiciosAdicionales
 * @property {number} total
 */

/**
 * Convierte la respuesta de POST /api/v1/cotizaciones; los importes llegan como número o texto.
 * @param {Record<string, any>} dato
 * @returns {Cotizacion}
 */
export function cotizacionDesdeApi(dato) {
  return {
    noches: dato.noches,
    precioNoche: Number(dato.precioNoche),
    precioBase: Number(dato.precioBase),
    tarifaLimpieza: Number(dato.tarifaLimpieza),
    tarifaServicio: Number(dato.tarifaServicio),
    descuento: Number(dato.descuento),
    serviciosAdicionales: (dato.serviciosAdicionales ?? []).map((adicional) => ({
      id: adicional.id,
      nombre: adicional.nombre,
      costo: Number(adicional.costo),
    })),
    totalServiciosAdicionales: Number(dato.totalServiciosAdicionales),
    total: Number(dato.total),
  };
}
