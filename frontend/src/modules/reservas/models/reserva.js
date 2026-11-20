/**
 * Reserva sobre una cotización vigente (SOF-33) con el estado de su pago (SOF-34).
 * @typedef {Object} Reserva
 * @property {number} id
 * @property {number} publicacionId
 * @property {string} llegada fecha en formato AAAA-MM-DD
 * @property {string} salida fecha en formato AAAA-MM-DD
 * @property {'PENDIENTE'|'CONFIRMADA'|'CANCELADA'|'VENCIDA'} estado
 * @property {'SIN_PAGO'|'APROBADO'|'RECHAZADO'|'ERROR'|'REEMBOLSADO'|'NO_COBRADO'} estadoPago
 * @property {string|null} transaccionId identificador de la transacción en la pasarela, si se aprobó o se reembolsó
 * @property {number} noches
 * @property {number} precioBase
 * @property {number} tarifaLimpieza
 * @property {number} tarifaServicio
 * @property {number} descuento
 * @property {number} totalServiciosAdicionales
 * @property {number} total
 */

/**
 * Convierte la respuesta de POST /api/v1/reservas y de su pago; los importes llegan como número o texto.
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
    estadoPago: dato.estadoPago,
    transaccionId: dato.transaccionId ?? null,
  };
}
