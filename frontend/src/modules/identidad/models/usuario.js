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

/**
 * @typedef {Object} Perfil
 * @property {number} id
 * @property {string} nombre
 * @property {string} correo
 * @property {string} telefono vacío si no tiene
 * @property {string} fotoUrl vacío si no tiene
 * @property {string} rol
 */

/**
 * @typedef {Object} DatosPerfil
 * @property {string} nombre
 * @property {string} telefono
 * @property {string} fotoUrl
 */

// Misma regla de formato que valida la API: 8 caracteres, una mayúscula, un
// número y un carácter especial (ni letra, ni número, ni espacio).
const CONTRASENA_VALIDA = /^(?=.*\p{Lu})(?=.*\p{Nd})(?=.*[^\p{L}\p{N}\s]).{8,72}$/u;
const CORREO_VALIDO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CODIGO_VALIDO = /^\d{6}$/;
// Mismos formatos que valida la API para el perfil (ActualizarPerfilRequest).
const TELEFONO_VALIDO = /^\+?\d{7,15}$/;
const FOTO_VALIDA = /^https?:\/\/\S+$/;
const MENSAJE_CONTRASENA = 'La contraseña debe tener al menos 8 caracteres, una mayúscula, un número y un carácter especial.';

/**
 * @param {string} correo
 * @returns {string | undefined} mensaje de error, o nada si el formato es válido
 */
function errorDeCorreo(correo) {
  if (!correo.trim()) {
    return 'Escribe tu correo.';
  }
  return CORREO_VALIDO.test(correo.trim()) ? undefined : 'El correo no tiene un formato válido.';
}

/**
 * Los mismos requisitos de CONTRASENA_VALIDA, uno por uno, para mostrarlos mientras se escribe.
 * @type {{id: string, texto: string, cumple: (contrasena: string) => boolean}[]}
 */
export const REQUISITOS_CONTRASENA = [
  { id: 'longitud', texto: 'Al menos 8 caracteres', cumple: (contrasena) => contrasena.length >= 8 },
  { id: 'mayuscula', texto: 'Una mayúscula', cumple: (contrasena) => /\p{Lu}/u.test(contrasena) },
  { id: 'numero', texto: 'Un número', cumple: (contrasena) => /\p{Nd}/u.test(contrasena) },
  { id: 'especial', texto: 'Un carácter especial', cumple: (contrasena) => /[^\p{L}\p{N}\s]/u.test(contrasena) },
];

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
  const errorCorreo = errorDeCorreo(datos.correo);
  if (errorCorreo) {
    errores.correo = errorCorreo;
  }
  if (!CONTRASENA_VALIDA.test(datos.contrasena)) {
    errores.contrasena = MENSAJE_CONTRASENA;
  }
  return errores;
}

/**
 * Solo exige los datos: la API decide si las credenciales son correctas.
 * @param {{correo: string, contrasena: string}} datos
 * @returns {Partial<Record<'correo' | 'contrasena', string>>} errores por campo
 */
export function validarInicioSesion(datos) {
  const errores = {};
  const errorCorreo = errorDeCorreo(datos.correo);
  if (errorCorreo) {
    errores.correo = errorCorreo;
  }
  if (!datos.contrasena) {
    errores.contrasena = 'Escribe tu contraseña.';
  }
  return errores;
}

/**
 * @param {{correo: string}} datos
 * @returns {Partial<Record<'correo', string>>} errores por campo
 */
export function validarSolicitudRecuperacion(datos) {
  const errorCorreo = errorDeCorreo(datos.correo);
  return errorCorreo ? { correo: errorCorreo } : {};
}

/**
 * La contraseña nueva sigue la misma regla de formato que el registro.
 * @param {{codigo: string, contrasenaNueva: string}} datos
 * @returns {Partial<Record<'codigo' | 'contrasenaNueva', string>>} errores por campo
 */
export function validarConfirmacionRecuperacion(datos) {
  const errores = {};
  if (!CODIGO_VALIDO.test(datos.codigo.trim())) {
    errores.codigo = 'Escribe el código de 6 dígitos.';
  }
  if (!CONTRASENA_VALIDA.test(datos.contrasenaNueva)) {
    errores.contrasenaNueva = MENSAJE_CONTRASENA;
  }
  return errores;
}

/**
 * Teléfono y foto son opcionales: solo se valida su formato si traen algo.
 * @param {DatosPerfil} datos
 * @returns {Partial<Record<keyof DatosPerfil, string>>} errores por campo
 */
export function validarPerfil(datos) {
  const errores = {};
  if (!datos.nombre.trim()) {
    errores.nombre = 'Escribe tu nombre.';
  }
  const telefono = datos.telefono.trim();
  if (telefono && !TELEFONO_VALIDO.test(telefono)) {
    errores.telefono = 'El teléfono debe tener entre 7 y 15 dígitos y puede empezar con +.';
  }
  const fotoUrl = datos.fotoUrl.trim();
  if (fotoUrl && !FOTO_VALIDA.test(fotoUrl)) {
    errores.fotoUrl = 'La foto debe ser una dirección que empiece con http:// o https://.';
  }
  return errores;
}

/**
 * Solo exige la actual: si es correcta lo decide la API. La nueva sigue la regla del registro.
 * @param {{contrasenaActual: string, contrasenaNueva: string}} datos
 * @returns {Partial<Record<'contrasenaActual' | 'contrasenaNueva', string>>} errores por campo
 */
export function validarCambioContrasena(datos) {
  const errores = {};
  if (!datos.contrasenaActual) {
    errores.contrasenaActual = 'Escribe tu contraseña actual.';
  }
  if (!CONTRASENA_VALIDA.test(datos.contrasenaNueva)) {
    errores.contrasenaNueva = MENSAJE_CONTRASENA;
  }
  return errores;
}

/**
 * @param {{id: number, nombre: string, correo: string, telefono: ?string, fotoUrl: ?string, rol: string}} respuesta
 * @returns {Perfil}
 */
export function perfilDesdeApi(respuesta) {
  return {
    id: respuesta.id,
    nombre: respuesta.nombre,
    correo: respuesta.correo,
    telefono: respuesta.telefono ?? '',
    fotoUrl: respuesta.fotoUrl ?? '',
    rol: respuesta.rol,
  };
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
