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
 * @property {UsuarioSesion} usuario
 */

/**
 * @typedef {Object} UsuarioSesion
 * @property {number} id
 * @property {string} nombre
 * @property {string} correo
 * @property {string} rol
 * @property {boolean} activa
 */

function leerSesionDe(crudo) {
  if (crudo === cache.crudo) {
    return cache.valor;
  }
  return crudo === null ? null : parsear(crudo);
}

/** @returns {Sesion | null} la sesión vigente; una vencida cuenta como ausente y se borra */
export function obtenerSesion() {
  const crudo = leerCrudo();
  const sesion = leerSesionDe(crudo);
  // El vencimiento se revisa también con la sesión en cache: puede vencer con la pestaña abierta.
  // No se notifica desde el getter (se llama durante el render); eso lo hace el timer de expiración.
  // Una fecha ilegible (NaN) también cuenta como vencida, igual que antes.
  const venceEn = sesion ? new Date(sesion.expiraEn).getTime() : 0;
  if (sesion && (Number.isNaN(venceEn) || venceEn <= Date.now())) {
    borrarCrudo();
    cache = { crudo: null, valor: null };
    return null;
  }
  if (crudo !== cache.crudo) {
    cache = { crudo, valor: sesion };
  }
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
