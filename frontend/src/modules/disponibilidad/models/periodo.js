/**
 * Periodo en que el anfitrión ofrece su alojamiento. Las fechas son ISO (AAAA-MM-DD);
 * la final es el día de salida y no cuenta como noche disponible.
 * @typedef {Object} Periodo
 * @property {number} id
 * @property {number} publicacionId
 * @property {string} fechaInicio
 * @property {string} fechaFin
 */

const FORMATO_FECHA = new Intl.DateTimeFormat('es-CO', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

/**
 * @param {Periodo} respuesta
 * @returns {Periodo}
 */
export function periodoDesdeApi(respuesta) {
  return {
    id: respuesta.id,
    publicacionId: respuesta.publicacionId,
    fechaInicio: respuesta.fechaInicio,
    fechaFin: respuesta.fechaFin,
  };
}

/**
 * Fecha ISO legible ("10 de enero de 2027"). Se lee en UTC para que la zona del
 * navegador no la corra un día.
 * @param {string} fechaIso
 */
export function fechaLegible(fechaIso) {
  return FORMATO_FECHA.format(new Date(`${fechaIso}T00:00:00Z`));
}
