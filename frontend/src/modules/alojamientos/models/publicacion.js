/**
 * @typedef {Object} Publicacion
 * @property {number} id
 * @property {string} titulo
 * @property {string | null} descripcion
 * @property {string | null} ciudad
 * @property {string | null} direccion
 * @property {string | null} tipo código que define la API (CASA, APARTAMENTO, HABITACION)
 * @property {number | null} capacidad
 * @property {number | null} precioNoche
 * @property {string[]} servicios
 * @property {string | null} reglas
 * @property {string[]} imagenes en orden; la primera es la principal
 * @property {string} estado
 * @property {string[]} camposFaltantes lo que la API dice que falta para activarla
 */

/**
 * Datos del formulario tal como los escribe el anfitrión: cada campo es texto.
 * @typedef {Object} FormularioPublicacion
 * @property {string} titulo
 * @property {string} descripcion
 * @property {string} ciudad
 * @property {string} direccion
 * @property {string} tipo código del tipo, o vacío
 * @property {string} capacidad
 * @property {string} precioNoche
 * @property {string} reglas
 */

/**
 * Tipos que acepta la API. Solo se traduce el nombre para mostrarlo; si el
 * código es válido lo decide la API.
 */
export const TIPOS_ALOJAMIENTO = [
  { codigo: 'APARTAMENTO', nombre: 'Apartamento' },
  { codigo: 'CASA', nombre: 'Casa' },
  { codigo: 'HABITACION', nombre: 'Habitación' },
];

/** @type {FormularioPublicacion} */
export const FORMULARIO_VACIO = {
  titulo: '', descripcion: '', ciudad: '', direccion: '', tipo: '', capacidad: '', precioNoche: '', reglas: '',
};

const ENTERO_POSITIVO = /^[1-9]\d*$/;
const PRECIO_VALIDO = /^\d{1,10}(\.\d{1,2})?$/;

/**
 * Valida solo el formato, con los mismos límites de la API; si los datos sirven
 * para activar la publicación lo decide la API.
 * @param {FormularioPublicacion} formulario
 * @returns {Partial<Record<keyof FormularioPublicacion, string>>} errores por campo
 */
export function validarFormulario(formulario) {
  const errores = {};
  if (!formulario.titulo.trim()) {
    errores.titulo = 'El título es obligatorio.';
  } else if (formulario.titulo.trim().length > 120) {
    errores.titulo = 'El título admite máximo 120 caracteres.';
  }
  if (formulario.capacidad.trim() && !ENTERO_POSITIVO.test(formulario.capacidad.trim())) {
    errores.capacidad = 'La capacidad debe ser un número entero mayor que cero.';
  }
  const precio = formulario.precioNoche.trim();
  if (precio && (!PRECIO_VALIDO.test(precio) || Number(precio) <= 0)) {
    errores.precioNoche = 'El precio por noche debe ser mayor que cero, con máximo 2 decimales.';
  }
  return errores;
}

/**
 * @param {Publicacion} publicacion
 * @returns {FormularioPublicacion}
 */
export function formularioDesde(publicacion) {
  const texto = (valor) => (valor === null || valor === undefined ? '' : String(valor));
  return {
    titulo: publicacion.titulo,
    descripcion: texto(publicacion.descripcion),
    ciudad: texto(publicacion.ciudad),
    direccion: texto(publicacion.direccion),
    tipo: texto(publicacion.tipo),
    capacidad: texto(publicacion.capacidad),
    precioNoche: texto(publicacion.precioNoche),
    reglas: texto(publicacion.reglas),
  };
}

const SIN_SERVICIOS_NI_IMAGENES = Object.freeze({ servicios: [], imagenes: [] });

/**
 * Arma el cuerpo que espera la API. Los servicios y las imágenes se envían tal
 * como están, porque el PUT reemplaza la publicación completa y lo que no llegue se borra.
 * @param {FormularioPublicacion} formulario
 * @param {{servicios: string[], imagenes: string[]}} [actuales]
 */
export function solicitudDesde(formulario, actuales = SIN_SERVICIOS_NI_IMAGENES) {
  const opcional = (valor) => valor.trim() || null;
  const numero = (valor) => (valor.trim() ? Number(valor.trim()) : null);
  return {
    titulo: formulario.titulo.trim(),
    descripcion: opcional(formulario.descripcion),
    ciudad: opcional(formulario.ciudad),
    direccion: opcional(formulario.direccion),
    tipo: opcional(formulario.tipo),
    capacidad: numero(formulario.capacidad),
    precioNoche: numero(formulario.precioNoche),
    reglas: opcional(formulario.reglas),
    servicios: actuales.servicios,
    imagenes: actuales.imagenes,
  };
}

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
