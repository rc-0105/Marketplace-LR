// Cliente HTTP único. Las rutas son relativas: en desarrollo Vite reenvía /api
// al backend (vite.config.js). Si hay sesión vigente se agrega el token como Bearer.

import { limpiarSesion, obtenerSesion } from './sesion.js';

const MENSAJE_SIN_CONEXION = 'No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.';
const MENSAJE_INESPERADO = 'Ocurrió un error inesperado. Intenta de nuevo.';
const PREFIJO_AUTH = '/api/v1/auth/';

/** Error de la API con el formato ProblemDetail que devuelve el backend. */
export class ErrorApi extends Error {
  /**
   * @param {number} estado código HTTP; 0 si no hubo respuesta
   * @param {string} detalle mensaje para mostrar al usuario
   * @param {Record<string, string>} [errores] errores por campo
   */
  constructor(estado, detalle, errores = {}) {
    super(detalle);
    this.name = 'ErrorApi';
    this.estado = estado;
    this.errores = errores;
  }
}

async function leerJson(respuesta) {
  const texto = await respuesta.text();
  if (!texto) {
    return null;
  }
  try {
    return JSON.parse(texto);
  } catch {
    return null;
  }
}

async function solicitar(ruta, opciones) {
  const sesion = obtenerSesion();
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json' };
  if (sesion) {
    headers.Authorization = `Bearer ${sesion.token}`;
  }

  let respuesta;
  try {
    respuesta = await fetch(ruta, { ...opciones, headers });
  } catch {
    throw new ErrorApi(0, MENSAJE_SIN_CONEXION);
  }

  const datos = await leerJson(respuesta);
  if (!respuesta.ok) {
    // Solo un 401 con token enviado fuera de /auth/ significa sesión vencida o revocada.
    // Un 401 de login son credenciales malas, y ningún otro estado (503, 500) toca la sesión.
    // Y solo si el token enviado sigue siendo el actual: un 401 tardío de un token viejo no debe borrar una sesión nueva.
    if (respuesta.status === 401 && sesion && !ruta.startsWith(PREFIJO_AUTH) && obtenerSesion()?.token === sesion.token) {
      limpiarSesion('expirada');
    }
    throw new ErrorApi(respuesta.status, datos?.detail ?? MENSAJE_INESPERADO, datos?.errores ?? {});
  }
  return datos;
}

export function post(ruta, cuerpo) {
  return solicitar(ruta, { method: 'POST', body: JSON.stringify(cuerpo) });
}
