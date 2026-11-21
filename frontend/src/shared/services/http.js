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
   * @param {{codigo?: string, motivo?: string}} [extra] código de negocio y motivo que envía la API
   */
  constructor(estado, detalle, errores = {}, extra = {}) {
    super(detalle);
    this.name = 'ErrorApi';
    this.estado = estado;
    this.errores = errores;
    this.codigo = extra.codigo ?? null;
    this.motivo = extra.motivo ?? null;
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

/** Envía la petición y devuelve la respuesta si fue exitosa; si no, lanza ErrorApi con el ProblemDetail. */
async function enviar(ruta, opciones, aceptar) {
  const sesion = obtenerSesion();
  const { cabeceras = {}, ...peticion } = opciones;
  const headers = { ...cabeceras, Accept: aceptar };
  // Con un archivo el navegador fija el Content-Type con su separador (boundary): no se debe pisar.
  if (!(peticion.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }
  if (sesion) {
    headers.Authorization = `Bearer ${sesion.token}`;
  }

  let respuesta;
  try {
    respuesta = await fetch(ruta, { ...peticion, headers });
  } catch {
    throw new ErrorApi(0, MENSAJE_SIN_CONEXION);
  }

  if (!respuesta.ok) {
    const datos = await leerJson(respuesta);
    // Solo un 401 con token enviado fuera de /auth/ significa sesión vencida o revocada.
    // Un 401 de login son credenciales malas, y ningún otro estado (503, 500) toca la sesión.
    // Y solo si el token enviado sigue siendo el actual: un 401 tardío de un token viejo no debe borrar una sesión nueva.
    if (respuesta.status === 401 && sesion && !ruta.startsWith(PREFIJO_AUTH) && obtenerSesion()?.token === sesion.token) {
      limpiarSesion('expirada');
    }
    throw new ErrorApi(respuesta.status, datos?.detail ?? MENSAJE_INESPERADO, datos?.errores ?? {},
      { codigo: datos?.codigo, motivo: datos?.motivo });
  }
  return respuesta;
}

async function solicitar(ruta, opciones) {
  return leerJson(await enviar(ruta, opciones, 'application/json'));
}

export function get(ruta) {
  return solicitar(ruta, { method: 'GET' });
}

/**
 * Descarga un archivo con GET. El nombre sale de la cabecera Content-Disposition que envía la API.
 * @returns {Promise<{contenido: Blob, nombre: string}>}
 */
export async function descargar(ruta) {
  const respuesta = await enviar(ruta, { method: 'GET' }, '*/*');
  const disposicion = respuesta.headers.get('Content-Disposition') ?? '';
  const nombre = /filename="([^"]+)"/.exec(disposicion)?.[1] ?? 'archivo';
  return { contenido: await respuesta.blob(), nombre };
}

/** `cabeceras` agrega cabeceras propias de la operación, como `Idempotency-Key`. */
export function post(ruta, cuerpo, cabeceras = {}) {
  return solicitar(ruta, { method: 'POST', body: JSON.stringify(cuerpo), cabeceras });
}

/** Envía un archivo como multipart: `datos` es un FormData con el archivo ya adjunto. */
export function subirArchivo(ruta, datos) {
  return solicitar(ruta, { method: 'POST', body: datos });
}

export function put(ruta, cuerpo) {
  return solicitar(ruta, { method: 'PUT', body: JSON.stringify(cuerpo) });
}

// `delete` es palabra reservada; un 204 sin cuerpo resuelve en null.
export function eliminar(ruta) {
  return solicitar(ruta, { method: 'DELETE' });
}
