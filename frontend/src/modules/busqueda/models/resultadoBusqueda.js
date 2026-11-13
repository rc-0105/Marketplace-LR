/**
 * Alojamiento que devuelve la búsqueda (RF-28).
 * @typedef {Object} ResultadoBusqueda
 * @property {number} id
 * @property {string} titulo
 * @property {string} ciudad
 * @property {string | null} tipo
 * @property {number | null} capacidad
 * @property {number | null} precioNoche
 * @property {string | null} imagenPrincipal
 */

/**
 * Convierte un resultado de GET /api/v1/busqueda; el precio llega como número o texto.
 * @param {Record<string, any>} dato
 * @returns {ResultadoBusqueda}
 */
export function resultadoDesdeApi(dato) {
  return {
    id: dato.id,
    titulo: dato.titulo,
    ciudad: dato.ciudad,
    tipo: dato.tipo ?? null,
    capacidad: dato.capacidad ?? null,
    precioNoche: dato.precioNoche == null ? null : Number(dato.precioNoche),
    imagenPrincipal: dato.imagenPrincipal ?? null,
  };
}
