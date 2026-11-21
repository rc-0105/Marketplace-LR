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

const MS_POR_DIA = 24 * 60 * 60 * 1000;

/**
 * Noches que ofrece un periodo: la fecha final es el día de salida y no cuenta.
 * @param {Periodo} periodo
 */
export function nochesDe({ fechaInicio, fechaFin }) {
  return Math.round((Date.parse(`${fechaFin}T00:00:00Z`) - Date.parse(`${fechaInicio}T00:00:00Z`)) / MS_POR_DIA);
}

/**
 * Dónde está el periodo respecto de hoy. Las fechas ISO se comparan como texto.
 * @param {Periodo} periodo
 * @param {string} hoy fecha ISO (AAAA-MM-DD)
 * @returns {'PROXIMO' | 'EN_CURSO' | 'PASADO'}
 */
export function momentoDe({ fechaInicio, fechaFin }, hoy) {
  if (fechaFin <= hoy) {
    return 'PASADO';
  }
  return fechaInicio > hoy ? 'PROXIMO' : 'EN_CURSO';
}

export const ETIQUETAS_MOMENTO = {
  PROXIMO: 'Próximo',
  EN_CURSO: 'En curso',
  PASADO: 'Pasado',
};
