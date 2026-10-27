// Cliente HTTP único. Las rutas son relativas: en desarrollo Vite reenvía /api
// al backend (vite.config.js). El token se agrega aquí cuando llegue SOF-12.

const MENSAJE_SIN_CONEXION = 'No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.';
const MENSAJE_INESPERADO = 'Ocurrió un error inesperado. Intenta de nuevo.';

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
  let respuesta;
  try {
    respuesta = await fetch(ruta, {
      ...opciones,
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    });
  } catch {
    throw new ErrorApi(0, MENSAJE_SIN_CONEXION);
  }

  const datos = await leerJson(respuesta);
  if (!respuesta.ok) {
    throw new ErrorApi(respuesta.status, datos?.detail ?? MENSAJE_INESPERADO, datos?.errores ?? {});
  }
  return datos;
}

export function post(ruta, cuerpo) {
  return solicitar(ruta, { method: 'POST', body: JSON.stringify(cuerpo) });
}
