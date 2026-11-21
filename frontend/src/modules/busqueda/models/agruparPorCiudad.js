/**
 * Agrupa por ciudad sin distinguir mayúsculas ni espacios; las ciudades salen en el orden en que aparecen.
 * @param {import('./resultadoBusqueda.js').ResultadoBusqueda[]} alojamientos
 * @returns {{ciudad: string, alojamientos: import('./resultadoBusqueda.js').ResultadoBusqueda[]}[]}
 */
export function agruparPorCiudad(alojamientos) {
  const grupos = new Map();
  for (const alojamiento of alojamientos) {
    const nombre = (alojamiento.ciudad ?? '').trim();
    const clave = nombre.toLowerCase();
    if (!grupos.has(clave)) {
      grupos.set(clave, { ciudad: nombre, alojamientos: [] });
    }
    grupos.get(clave).alojamientos.push(alojamiento);
  }
  return [...grupos.values()];
}
