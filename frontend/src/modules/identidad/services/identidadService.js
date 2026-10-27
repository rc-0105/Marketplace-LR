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
