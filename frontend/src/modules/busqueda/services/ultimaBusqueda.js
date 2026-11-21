// Última búsqueda del huésped: para que al entrar a un alojamiento y volver no tenga que escribir de nuevo
// la ciudad, las fechas y los filtros. Vive en sessionStorage, igual que la sesión: se pierde al cerrar la pestaña.

const CLAVE = 'marketplace.ultimaBusqueda';
const TEXTOS = ['ciudad', 'llegada', 'salida', 'capacidad', 'tipo', 'precioMin', 'precioMax'];

/**
 * @param {{ciudad: string, llegada: string, salida: string, capacidad: string, tipo: string,
 *   precioMin: string, precioMax: string, servicios: number[]}} criterios
 */
export function guardarUltimaBusqueda(criterios) {
  try {
    sessionStorage.setItem(CLAVE, JSON.stringify(criterios));
  } catch {
    // Sin almacenamiento la búsqueda simplemente no se recuerda.
  }
}

/**
 * Devuelve solo los campos conocidos y con el tipo esperado, así un valor dañado no rompe el formulario.
 * @returns {Partial<Parameters<typeof guardarUltimaBusqueda>[0]> | null} null si no hay nada guardado
 */
export function leerUltimaBusqueda() {
  let guardado;
  try {
    guardado = JSON.parse(sessionStorage.getItem(CLAVE));
  } catch {
    return null;
  }
  if (guardado === null || typeof guardado !== 'object' || Array.isArray(guardado)) return null;

  const criterios = {};
  TEXTOS.forEach((campo) => {
    if (typeof guardado[campo] === 'string') criterios[campo] = guardado[campo];
  });
  if (Array.isArray(guardado.servicios)) {
    criterios.servicios = guardado.servicios.filter((id) => Number.isInteger(id));
  }
  return criterios;
}
