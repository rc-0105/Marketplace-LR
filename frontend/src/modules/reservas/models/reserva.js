/**
 * Reserva sobre una cotización vigente (SOF-33) con el estado de su pago (SOF-34).
 * @typedef {Object} Reserva
 * @property {number} id
 * @property {number} publicacionId
 * @property {string} llegada fecha en formato AAAA-MM-DD
 * @property {string} salida fecha en formato AAAA-MM-DD
 * @property {'PENDIENTE'|'CONFIRMADA'|'CANCELADA'|'VENCIDA'|'COMPLETADA'} estado
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
 * Una fila de "mis reservas" (SOF-35). Título, ciudad e imagen son null si la publicación ya no existe.
 * @typedef {Object} ResumenReserva
 * @property {number} id
 * @property {number} publicacionId
 * @property {string|null} titulo
 * @property {string|null} ciudad
 * @property {string|null} imagenPrincipal
 * @property {string} llegada fecha en formato AAAA-MM-DD
 * @property {string} salida fecha en formato AAAA-MM-DD
 * @property {number} noches
 * @property {Reserva['estado']} estado estado actual: una pendiente con el apartado agotado llega VENCIDA
 * @property {number} total
 */

/**
 * Una reserva abierta por su huésped (SOF-35): el alojamiento (null si ya no existe), el desglose fijo y el pago.
 * Los servicios adicionales traen su nombre actual, o null si el anfitrión lo borró; solo se guarda su total.
 * @typedef {Object} DetalleReserva
 * @property {number} id
 * @property {{id: number, titulo: string, ciudad: string|null, imagenPrincipal: string|null}|null} alojamiento
 * @property {string} llegada
 * @property {string} salida
 * @property {Reserva['estado']} estado
 * @property {string} venceEn instante ISO hasta el que una pendiente aparta las noches
 * @property {number} noches
 * @property {number} precioBase
 * @property {number} tarifaLimpieza
 * @property {number} tarifaServicio
 * @property {number} descuento
 * @property {{id: number, nombre: string|null}[]} serviciosAdicionales
 * @property {number} totalServiciosAdicionales
 * @property {number} total
 * @property {Reserva['estadoPago']} estadoPago
 * @property {string|null} transaccionId
 */

const NOMBRE_ESTADO_RESERVA = {
  PENDIENTE: 'Pendiente de pago',
  CONFIRMADA: 'Confirmada',
  CANCELADA: 'Cancelada',
  VENCIDA: 'Vencida',
  COMPLETADA: 'Completada',
};

// ERROR es un pago que la pasarela no confirmó a tiempo y la conciliación todavía resuelve (SOF-34).
const NOMBRE_ESTADO_PAGO = {
  SIN_PAGO: 'Sin pago',
  APROBADO: 'Aprobado',
  RECHAZADO: 'Rechazado',
  ERROR: 'En verificación',
  REEMBOLSADO: 'Reembolsado',
  NO_COBRADO: 'No cobrado',
};

const FORMATO_PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
// Las fechas de la reserva son días sin hora: se formatean en UTC para que la zona del navegador no las corra.
const FORMATO_FECHA = new Intl.DateTimeFormat('es-CO', {
  day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC',
});

/** @param {Reserva['estado']} estado */
export function nombreDeEstadoReserva(estado) {
  return NOMBRE_ESTADO_RESERVA[estado] ?? estado;
}

/** @param {Reserva['estadoPago']} estado */
export function nombreDeEstadoPago(estado) {
  return NOMBRE_ESTADO_PAGO[estado] ?? estado;
}

/** @param {number} valor importe en pesos colombianos */
export function formatearPesos(valor) {
  return FORMATO_PESOS.format(valor);
}

/** @param {string} fecha día en formato AAAA-MM-DD */
export function formatearFecha(fecha) {
  return FORMATO_FECHA.format(new Date(`${fecha}T00:00:00Z`));
}

/**
 * Convierte una fila de GET /api/v1/reservas/mias.
 * @param {Record<string, any>} dato
 * @returns {ResumenReserva}
 */
export function resumenReservaDesdeApi(dato) {
  return {
    id: dato.id,
    publicacionId: dato.publicacionId,
    titulo: dato.titulo ?? null,
    ciudad: dato.ciudad ?? null,
    imagenPrincipal: dato.imagenPrincipal ?? null,
    llegada: dato.llegada,
    salida: dato.salida,
    noches: dato.noches,
    estado: dato.estado,
    total: Number(dato.total),
  };
}

/**
 * Convierte la respuesta de GET /api/v1/reservas/{id}.
 * @param {Record<string, any>} dato
 * @returns {DetalleReserva}
 */
export function detalleReservaDesdeApi(dato) {
  return {
    id: dato.id,
    alojamiento: dato.alojamiento ?? null,
    llegada: dato.llegada,
    salida: dato.salida,
    estado: dato.estado,
    venceEn: dato.venceEn,
    noches: dato.noches,
    precioBase: Number(dato.precioBase),
    tarifaLimpieza: Number(dato.tarifaLimpieza),
    tarifaServicio: Number(dato.tarifaServicio),
    descuento: Number(dato.descuento),
    serviciosAdicionales: dato.serviciosAdicionales ?? [],
    totalServiciosAdicionales: Number(dato.totalServiciosAdicionales),
    total: Number(dato.total),
    estadoPago: dato.estadoPago,
    transaccionId: dato.transaccionId ?? null,
  };
}

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

/**
 * Resultado de cancelar una reserva (SOF-75): el valor de devolución es siempre el 100% del total.
 * @typedef {Object} Cancelacion
 * @property {number} reservaId
 * @property {string} estado estado final de la reserva, 'CANCELADA'
 * @property {string} motivo
 * @property {number} valorDevolucion
 * @property {string} fecha instante en que se registró la cancelación
 */

/**
 * Convierte la respuesta de POST /api/v1/reservas/{id}/cancelacion; el importe llega como número o texto.
 * @param {Record<string, any>} dato
 * @returns {Cancelacion}
 */
export function cancelacionDesdeApi(dato) {
  return {
    reservaId: dato.reservaId,
    estado: dato.estado,
    motivo: dato.motivo,
    valorDevolucion: Number(dato.valorDevolucion),
    fecha: dato.fecha,
  };
}
