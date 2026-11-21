import { useSyncExternalStore } from 'react';
import { obtenerNoLeidas, suscribirNoLeidas } from './contadorNotificaciones.js';

/** Lee el contador compartido de notificaciones sin leer. */
export function useNoLeidas() {
  return useSyncExternalStore(suscribirNoLeidas, obtenerNoLeidas);
}
