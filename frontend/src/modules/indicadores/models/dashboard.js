/**
 * @typedef {Object} CiudadDemanda
 * @property {string} ciudad
 * @property {number} reservas
 */

/**
 * @typedef {Object} Dashboard
 * @property {string|null} desde AAAA-MM-DD, o null si el rango no tiene inicio
 * @property {string|null} hasta AAAA-MM-DD, o null si el rango no tiene fin
 * @property {Record<string, number>} usuariosPorRol todos los roles, aunque el conteo sea 0
 * @property {number} publicacionesActivas
 * @property {Record<string, number>} reservasPorEstado todos los estados, aunque el conteo sea 0
 * @property {number} ingresosEstimados total de las reservas pagadas (confirmadas, completadas y en revisión
 *   manual), en pesos
 * @property {CiudadDemanda[]} ciudadesMayorDemanda como máximo cinco, de más a menos reservas
 */

const NOMBRE_ROL = { HUESPED: 'Huéspedes', ANFITRION: 'Anfitriones', ADMINISTRADOR: 'Administradores' };

const NOMBRE_ESTADO_RESERVA = {
  PENDIENTE: 'Pendientes',
  CONFIRMADA: 'Confirmadas',
  CANCELADA: 'Canceladas',
  VENCIDA: 'Vencidas',
  COMPLETADA: 'Completadas',
  EN_REVISION_MANUAL: 'En revisión manual',
};

/** @param {string} rol */
export function nombreDeRol(rol) {
  return NOMBRE_ROL[rol] ?? rol;
}

/** @param {string} estado */
export function nombreDeEstadoReserva(estado) {
  return NOMBRE_ESTADO_RESERVA[estado] ?? estado;
}

/** @param {Record<string, number>} conteos */
export function sumar(conteos) {
  return Object.values(conteos).reduce((total, valor) => total + valor, 0);
}
