// Store de la sesión del cliente. Vive en sessionStorage (nunca en localStorage):
// se pierde al cerrar la pestaña. Es la única fuente de verdad; los componentes
// la leen con useSesion() y http.js la consulta para enviar el token.

const CLAVE = 'marketplace.sesion';
const MOTIVO_EXPIRADA = 'expirada';

const oyentes = new Set();
const oyentesExpiracion = new Set();

// Memoiza por el string crudo: useSyncExternalStore exige la misma referencia
// mientras nada cambie, y parsear el JSON en cada lectura provocaría un loop.
let cache = { crudo: null, valor: null };

function leerCrudo() {
  try {
    return sessionStorage.getItem(CLAVE);
  } catch {
    return null;
  }
}

function borrarCrudo() {
  try {
    sessionStorage.removeItem(CLAVE);
  } catch {
    // Sin almacenamiento no hay nada que borrar.
  }
}

function parsear(crudo) {
  try {
    return JSON.parse(crudo);
  } catch {
    return null;
  }
}

function notificar(conjunto) {
  conjunto.forEach((oyente) => oyente());
}

/**
 * @typedef {Object} Sesion
 * @property {string} token
 * @property {string} expiraEn fecha ISO en la que vence el token
 * @property {import('../../modules/identidad/models/usuario.js').Usuario} usuario
 */

/** @returns {Sesion | null} la sesión vigente; una vencida cuenta como ausente y se borra */
export function obtenerSesion() {
  const crudo = leerCrudo();
  if (crudo === cache.crudo) {
    return cache.valor;
  }
  const sesion = crudo === null ? null : parsear(crudo);
  if (sesion && !(new Date(sesion.expiraEn) > new Date())) {
    borrarCrudo();
    cache = { crudo: null, valor: null };
    return null;
  }
  cache = { crudo, valor: sesion };
  return sesion;
}

/** @param {Sesion} sesion */
export function guardarSesion(sesion) {
  try {
    sessionStorage.setItem(CLAVE, JSON.stringify(sesion));
  } catch {
    // Sin almacenamiento la sesión no se conserva: se trata como sin sesión.
  }
  notificar(oyentes);
}

/** @param {string} [motivo] con 'expirada' se avisa además a los suscriptores de expiración */
export function limpiarSesion(motivo) {
  borrarCrudo();
  cache = { crudo: null, valor: null };
  notificar(oyentes);
  if (motivo === MOTIVO_EXPIRADA) {
    notificar(oyentesExpiracion);
  }
}

/** @returns {() => void} función para cancelar la suscripción */
export function suscribir(oyente) {
  oyentes.add(oyente);
  return () => oyentes.delete(oyente);
}

/** @returns {() => void} función para cancelar la suscripción */
export function suscribirExpiracion(oyente) {
  oyentesExpiracion.add(oyente);
  return () => oyentesExpiracion.delete(oyente);
}
