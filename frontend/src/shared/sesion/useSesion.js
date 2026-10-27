import { useSyncExternalStore } from 'react';
import { obtenerSesion, suscribir } from '../services/sesion.js';

/** Lee la sesión vigente del store. Devuelve null si no hay sesión. */
export function useSesion() {
  return useSyncExternalStore(suscribir, obtenerSesion);
}
