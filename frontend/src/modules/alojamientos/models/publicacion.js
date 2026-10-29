/**
 * @typedef {Object} OpcionCatalogo
 * @property {number} id
 * @property {string} nombre
 */

/**
 * @typedef {Object} Publicacion
 * @property {number} id
 * @property {string} titulo
 * @property {string | null} descripcion
 * @property {string | null} ciudad
 * @property {string | null} direccion
 * @property {OpcionCatalogo | null} tipo
 * @property {number | null} capacidad
 * @property {number | null} precioNoche
 * @property {OpcionCatalogo[]} servicios
 * @property {string | null} reglas
 * @property {string[]} imagenes en orden; la primera es la principal
 * @property {string} estado
 * @property {string[]} camposFaltantes lo que la API dice que falta para activarla
 */

const NOMBRE_ESTADO = {
  BORRADOR: 'Borrador',
};

/**
 * @param {string} estado
 * @returns {string} el estado como se le muestra al anfitrión
 */
export function nombreDeEstado(estado) {
  return NOMBRE_ESTADO[estado] ?? estado;
}

/**
 * @param {Object} respuesta publicación tal como la devuelve la API
 * @returns {Publicacion}
 */
export function publicacionDesdeApi(respuesta) {
  return {
    id: respuesta.id,
    titulo: respuesta.titulo,
    descripcion: respuesta.descripcion ?? null,
    ciudad: respuesta.ciudad ?? null,
    direccion: respuesta.direccion ?? null,
    tipo: respuesta.tipo ?? null,
    capacidad: respuesta.capacidad ?? null,
    precioNoche: respuesta.precioNoche ?? null,
    servicios: respuesta.servicios ?? [],
    reglas: respuesta.reglas ?? null,
    imagenes: respuesta.imagenes ?? [],
    estado: respuesta.estado,
    camposFaltantes: respuesta.camposFaltantes ?? [],
  };
}
