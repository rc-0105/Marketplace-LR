import { post } from '../../../shared/services/http.js';
import { guardarSesion, limpiarSesion } from '../../../shared/services/sesion.js';
import { usuarioDesdeApi } from '../models/usuario.js';

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
export async function iniciarSesion(credenciales) {
  const { token, expiraEn, usuario } = await post('/api/v1/auth/login', credenciales);
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
