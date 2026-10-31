import { get, post, put } from '../../../shared/services/http.js';
import { guardarSesion, limpiarSesion, obtenerSesion } from '../../../shared/services/sesion.js';
import { perfilDesdeApi, usuarioDesdeApi } from '../models/usuario.js';

/**
 * @param {import('../models/usuario.js').DatosRegistro} datos
 * @returns {Promise<import('../models/usuario.js').Usuario>}
 */
export async function registrarUsuario(datos) {
  return usuarioDesdeApi(await post('/api/v1/auth/registro', datos));
}

/**
 * Si la API rechaza las credenciales (400, 401) el ErrorApi se propaga y no se guarda nada.
 * @param {{correo: string, contrasena: string}} credenciales
 * @returns {Promise<import('../models/usuario.js').Usuario>}
 */
export function iniciarSesion(credenciales) {
  return abrirSesion('/api/v1/auth/login', credenciales);
}

/**
 * Reactiva una cuenta desactivada con sus propias credenciales y deja la sesión iniciada.
 * Si la API responde 401, 403 o 409 el ErrorApi se propaga y no se guarda nada.
 * @param {{correo: string, contrasena: string}} credenciales
 * @returns {Promise<import('../models/usuario.js').Usuario>}
 */
export function reactivarCuenta(credenciales) {
  return abrirSesion('/api/v1/perfil/reactivar', credenciales);
}

async function abrirSesion(ruta, credenciales) {
  const { token, expiraEn, usuario } = await post(ruta, credenciales);
  const usuarioSesion = usuarioDesdeApi(usuario);
  guardarSesion({ token, expiraEn, usuario: usuarioSesion });
  return usuarioSesion;
}

/**
 * La API responde lo mismo exista o no el correo; el mensaje se muestra tal cual.
 * @param {string} correo
 * @returns {Promise<string>} mensaje de la API
 */
export async function solicitarRecuperacion(correo) {
  const { mensaje } = await post('/api/v1/auth/recuperacion', { correo });
  return mensaje;
}

/**
 * Si el código no sirve (400) el ErrorApi se propaga con el mensaje de la API.
 * @param {{correo: string, codigo: string, contrasenaNueva: string}} datos
 * @returns {Promise<void>}
 */
export async function confirmarRecuperacion(datos) {
  await post('/api/v1/auth/recuperacion/confirmar', datos);
}

/**
 * Desactiva la cuenta propia confirmando con la contraseña. En 204 la sesión local queda limpia
 * sin llamar al logout (el servidor ya la invalidó). Ante un error el ErrorApi se propaga y la sesión sigue.
 * @param {string} contrasena
 */
export function desactivarCuenta(contrasena) {
  return darDeBaja('/api/v1/perfil/desactivar', contrasena);
}

/**
 * Elimina la cuenta propia (se anonimiza) confirmando con la contraseña; mismo contrato que desactivar.
 * @param {string} contrasena
 */
export function eliminarCuenta(contrasena) {
  return darDeBaja('/api/v1/perfil/eliminar', contrasena);
}

async function darDeBaja(ruta, contrasena) {
  await post(ruta, { contrasena });
  limpiarSesion();
}

/** @returns {Promise<import('../models/usuario.js').Perfil>} */
export async function obtenerPerfil() {
  return perfilDesdeApi(await get('/api/v1/perfil'));
}

/**
 * Guarda nombre, teléfono y foto; el correo no se envía porque la API no permite cambiarlo.
 * La sesión local se queda con el nombre nuevo. Si la API responde 400 el ErrorApi se propaga.
 * @param {import('../models/usuario.js').DatosPerfil} datos
 * @returns {Promise<import('../models/usuario.js').Perfil>}
 */
export async function actualizarPerfil(datos) {
  const perfil = perfilDesdeApi(await put('/api/v1/perfil', {
    nombre: datos.nombre, telefono: datos.telefono, fotoUrl: datos.fotoUrl,
  }));
  const sesion = obtenerSesion();
  if (sesion) {
    guardarSesion({ ...sesion, usuario: { ...sesion.usuario, nombre: perfil.nombre } });
  }
  return perfil;
}

/**
 * Si la actual es incorrecta (403) o la nueva no cumple la regla (400) el ErrorApi se propaga.
 * @param {{contrasenaActual: string, contrasenaNueva: string}} datos
 * @returns {Promise<void>}
 */
export async function cambiarContrasena(datos) {
  await put('/api/v1/perfil/contrasena', datos);
}

/** Siempre deja al usuario sin sesión local, falle o no el logout del servidor. */
export async function cerrarSesion() {
  try {
    await post('/api/v1/auth/logout');
  } catch {
    // El token vence solo en el servidor; localmente igual se cierra la sesión.
  } finally {
    limpiarSesion();
  }
}
