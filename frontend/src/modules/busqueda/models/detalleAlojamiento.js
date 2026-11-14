/**
 * Alojamiento tal como lo ve el huésped (RF-31).
 * @typedef {Object} DetalleAlojamiento
 * @property {number} id
 * @property {string} titulo
 * @property {string | null} descripcion
 * @property {string | null} ciudad
 * @property {string | null} tipo
 * @property {number | null} capacidad
 * @property {number | null} precioNoche
 * @property {string | null} reglas
 * @property {string[]} imagenes
 * @property {string[]} servicios
 * @property {string | null} politicaCancelacion
 * @property {string | null} descripcionPolitica
 * @property {{inicio: string, fin: string}[]} periodos fechas AAAA-MM-DD
 * @property {number | null} calificacionPromedio nulo mientras no tenga calificaciones
 */

/**
 * Convierte la respuesta de GET /api/v1/busqueda/{id}; el precio llega como número o texto.
 * @param {Record<string, any>} dato
 * @returns {DetalleAlojamiento}
 */
export function detalleDesdeApi(dato) {
  return {
    id: dato.id,
    titulo: dato.titulo,
    descripcion: dato.descripcion ?? null,
    ciudad: dato.ciudad ?? null,
    tipo: dato.tipo ?? null,
    capacidad: dato.capacidad ?? null,
    precioNoche: dato.precioNoche == null ? null : Number(dato.precioNoche),
    reglas: dato.reglas ?? null,
    imagenes: dato.imagenes ?? [],
    servicios: dato.servicios ?? [],
    politicaCancelacion: dato.politicaCancelacion ?? null,
    descripcionPolitica: dato.descripcionPolitica ?? null,
    periodos: dato.periodos ?? [],
    calificacionPromedio: dato.calificacionPromedio ?? null,
  };
}
