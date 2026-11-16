/**
 * Un día de la ventana consultada. El estado lo decide la API; `reservaId` solo llega
 * cuando quien consulta es el dueño de la publicación.
 * @typedef {Object} DiaCalendario
 * @property {string} fecha ISO (AAAA-MM-DD)
 * @property {'DISPONIBLE' | 'RESERVADO' | 'BLOQUEADO' | null} estado null en los días del mes que la API no devolvió
 * @property {number} [reservaId]
 */

/**
 * Calendario de una publicación en la ventana [desde, hasta): `hasta` no está en `dias`.
 * @typedef {Object} Calendario
 * @property {number} publicacionId
 * @property {string} desde
 * @property {string} hasta
 * @property {boolean} tienePeriodos false si la publicación no tiene ningún periodo cargado
 * @property {DiaCalendario[]} dias
 */

/**
 * Un mes listo para pintar: semanas de lunes a domingo, con null en las celdas de relleno.
 * @typedef {Object} MesCalendario
 * @property {string} clave AAAA-MM
 * @property {string} titulo "diciembre de 2026"
 * @property {(DiaCalendario | null)[][]} semanas
 */

export const ETIQUETAS_ESTADO = {
  DISPONIBLE: 'Disponible',
  RESERVADO: 'Reservado',
  BLOQUEADO: 'Bloqueado',
};

const FORMATO_MES = new Intl.DateTimeFormat('es-CO', { month: 'long', year: 'numeric', timeZone: 'UTC' });
const MS_POR_DIA = 24 * 60 * 60 * 1000;

/**
 * @param {Calendario} respuesta
 * @returns {Calendario}
 */
export function calendarioDesdeApi(respuesta) {
  return {
    publicacionId: respuesta.publicacionId,
    desde: respuesta.desde,
    hasta: respuesta.hasta,
    tienePeriodos: respuesta.tienePeriodos,
    dias: respuesta.dias.map((dia) => ({
      fecha: dia.fecha,
      estado: dia.estado,
      ...(dia.reservaId == null ? {} : { reservaId: dia.reservaId }),
    })),
  };
}

function aFecha(iso) {
  return new Date(`${iso}T00:00:00Z`);
}

function aIso(fecha) {
  return fecha.toISOString().slice(0, 10);
}

/**
 * Agrupa los días por mes calendario, del primero al último que toca la ventana. Cada mes se
 * completa con todos sus días: los que la API no devolvió quedan con `estado: null`.
 * @param {DiaCalendario[]} dias
 * @returns {MesCalendario[]}
 */
export function mesesDe(dias) {
  if (dias.length === 0) {
    return [];
  }
  const porFecha = new Map(dias.map((dia) => [dia.fecha, dia]));
  const primero = aFecha(dias[0].fecha);
  const ultimo = aFecha(dias[dias.length - 1].fecha);
  const meses = [];

  for (
    let inicio = new Date(Date.UTC(primero.getUTCFullYear(), primero.getUTCMonth(), 1));
    inicio <= ultimo;
    inicio = new Date(Date.UTC(inicio.getUTCFullYear(), inicio.getUTCMonth() + 1, 1))
  ) {
    const diasDelMes = new Date(Date.UTC(inicio.getUTCFullYear(), inicio.getUTCMonth() + 1, 0)).getUTCDate();
    // getUTCDay: domingo = 0. Con lunes primero, el desfase es (dia + 6) % 7.
    const celdas = Array((inicio.getUTCDay() + 6) % 7).fill(null);
    for (let numero = 0; numero < diasDelMes; numero += 1) {
      const fecha = aIso(new Date(inicio.getTime() + numero * MS_POR_DIA));
      celdas.push(porFecha.get(fecha) ?? { fecha, estado: null });
    }
    while (celdas.length % 7 !== 0) {
      celdas.push(null);
    }
    const semanas = [];
    for (let i = 0; i < celdas.length; i += 7) {
      semanas.push(celdas.slice(i, i + 7));
    }
    meses.push({ clave: aIso(inicio).slice(0, 7), titulo: FORMATO_MES.format(inicio), semanas });
  }
  return meses;
}
