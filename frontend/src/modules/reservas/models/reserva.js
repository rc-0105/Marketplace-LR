/**
 * Reserva confirmada sobre una cotización vigente (SOF-33).
 * @typedef {Object} Reserva
 * @property {number} id
 * @property {number} publicacionId
 * @property {string} llegada fecha en formato AAAA-MM-DD
 * @property {string} salida fecha en formato AAAA-MM-DD
 * @property {'PENDIENTE'|'CONFIRMADA'|'CANCELADA'} estado
 * @property {number} noches
 * @property {number} precioBase
 * @property {number} tarifaLimpieza
 * @property {number} tarifaServicio
 * @property {number} descuento
 * @property {number} totalServiciosAdicionales
 * @property {number} total
 */

/**
 * Convierte la respuesta de POST /api/v1/reservas; los importes llegan como número o texto.
 * @param {Record<string, any>} dato
 * @returns {Reserva}
 */
export function reservaDesdeApi(dato) {
  return {
    id: dato.id,
    publicacionId: dato.publicacionId,
    llegada: dato.llegada,
    salida: dato.salida,
    estado: dato.estado,
    noches: dato.noches,
    precioBase: Number(dato.precioBase),
    tarifaLimpieza: Number(dato.tarifaLimpieza),
    tarifaServicio: Number(dato.tarifaServicio),
    descuento: Number(dato.descuento),
    totalServiciosAdicionales: Number(dato.totalServiciosAdicionales),
    total: Number(dato.total),
  };
}
