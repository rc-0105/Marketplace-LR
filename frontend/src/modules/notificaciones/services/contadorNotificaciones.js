// Store del contador de notificaciones sin leer. Lo comparten la campana del encabezado y la página
// de notificaciones para que, al abrir una, el contador baje en ambas. Se lee con useNoLeidas().

const oyentes = new Set();
let noLeidas = 0;

function notificar() {
  oyentes.forEach((oyente) => oyente());
}

/** @returns {number} */
export function obtenerNoLeidas() {
  return noLeidas;
}

/** @param {number} valor el total de no leídas que informó la API */
export function fijarNoLeidas(valor) {
  const nuevo = Math.max(0, Number(valor) || 0);
  if (nuevo === noLeidas) return;
  noLeidas = nuevo;
  notificar();
}

/** Resta una no leída, sin bajar de cero. */
export function descontarNoLeida() {
  fijarNoLeidas(noLeidas - 1);
}

/** @returns {() => void} función para cancelar la suscripción */
export function suscribirNoLeidas(oyente) {
  oyentes.add(oyente);
  return () => oyentes.delete(oyente);
}
