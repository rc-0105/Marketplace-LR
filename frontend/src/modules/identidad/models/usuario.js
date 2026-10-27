/**
 * @typedef {'HUESPED' | 'ANFITRION'} RolRegistro
 */

/**
 * @typedef {Object} DatosRegistro
 * @property {string} nombre
 * @property {string} correo
 * @property {string} contrasena
 * @property {RolRegistro} rol
 */

/**
 * @typedef {Object} Usuario
 * @property {number} id
 * @property {string} nombre
 * @property {string} correo
 * @property {string} rol
 * @property {boolean} activa
 */

// Misma regla de formato que valida la API: 8 caracteres, una mayúscula y un
// carácter especial (ni letra, ni número, ni espacio).
const CONTRASENA_VALIDA = /^(?=.*\p{Lu})(?=.*[^\p{L}\p{N}\s]).{8,72}$/u;
const CORREO_VALIDO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const AYUDA_CONTRASENA = 'Mínimo 8 caracteres, con una mayúscula y un carácter especial.';

/**
 * Valida solo el formato del formulario; las reglas de negocio las decide la API.
 * @param {DatosRegistro} datos
 * @returns {Partial<Record<keyof DatosRegistro, string>>} errores por campo
 */
export function validarRegistro(datos) {
  const errores = {};
  if (!datos.nombre.trim()) {
    errores.nombre = 'Escribe tu nombre.';
  }
  if (!datos.correo.trim()) {
    errores.correo = 'Escribe tu correo.';
  } else if (!CORREO_VALIDO.test(datos.correo.trim())) {
    errores.correo = 'El correo no tiene un formato válido.';
  }
  if (!CONTRASENA_VALIDA.test(datos.contrasena)) {
    errores.contrasena = 'La contraseña debe tener al menos 8 caracteres, una mayúscula y un carácter especial.';
  }
  return errores;
}

/**
 * @param {{id: number, nombre: string, correo: string, rol: string, estado: string}} respuesta
 * @returns {Usuario}
 */
export function usuarioDesdeApi(respuesta) {
  return {
    id: respuesta.id,
    nombre: respuesta.nombre,
    correo: respuesta.correo,
    rol: respuesta.rol,
    activa: respuesta.estado === 'ACTIVA',
  };
}
