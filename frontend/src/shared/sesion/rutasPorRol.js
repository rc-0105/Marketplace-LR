// Panel de inicio de cada rol. Un rol desconocido cae en el inicio público.
const HOME_POR_ROL = {
  HUESPED: '/huesped',
  ANFITRION: '/anfitrion',
  ADMINISTRADOR: '/administrador',
};

/** @param {string} rol */
export function homeDe(rol) {
  return HOME_POR_ROL[rol] ?? '/';
}
