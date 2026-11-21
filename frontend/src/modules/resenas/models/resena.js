/**
 * Reseña y calificación de un huésped sobre su estancia (SOF-80, HU-35).
 * @typedef {Object} Resena
 * @property {number} id
 * @property {number} reservaId reserva que le dio origen
 * @property {number} publicacionId alojamiento reseñado
 * @property {number} calificacion de 1 a 5
 * @property {string} contenido
 * @property {string} fecha instante ISO en que se publicó
 */

export const CALIFICACION_MINIMA = 1;
export const CALIFICACION_MAXIMA = 5;
export const CONTENIDO_MAXIMO = 1000;

/** Las calificaciones posibles, de la menor a la mayor. */
export const CALIFICACIONES = [1, 2, 3, 4, 5];

/**
 * Convierte la respuesta de POST /api/v1/reservas/{id}/resena.
 * @param {Record<string, any>} dato
 * @returns {Resena}
 */
export function resenaDesdeApi(dato) {
  return {
    id: dato.id,
    reservaId: dato.reservaId,
    publicacionId: dato.publicacionId,
    calificacion: dato.calificacion,
    contenido: dato.contenido,
    fecha: dato.fecha,
  };
}

/**
 * Reseña visible de una publicación, con su autor (SOF-81, HU-36).
 * @typedef {Object} ResenaPublicada
 * @property {number} id
 * @property {number} reservaId reserva que le dio origen
 * @property {string} autor nombre del huésped
 * @property {number} calificacion de 1 a 5
 * @property {string} contenido
 * @property {string} fecha instante ISO en que se publicó
 */

/**
 * Las reseñas visibles de una publicación, de la más reciente a la más antigua.
 * @typedef {Object} ResenasPublicacion
 * @property {number | null} promedio con un decimal; nulo mientras no haya reseñas
 * @property {ResenaPublicada[]} resenas
 */

/**
 * Convierte la respuesta de GET /api/v1/publicaciones/{id}/resenas.
 * @param {Record<string, any>} dato
 * @returns {ResenasPublicacion}
 */
export function resenasPublicacionDesdeApi(dato) {
  return {
    promedio: dato.promedio ?? null,
    resenas: (dato.resenas ?? []).map((resena) => ({
      id: resena.id,
      reservaId: resena.reservaId,
      autor: resena.autor,
      calificacion: resena.calificacion,
      contenido: resena.contenido,
      fecha: resena.fecha,
    })),
  };
}
