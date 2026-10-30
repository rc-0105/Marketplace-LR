/**
 * @typedef {Object} OpcionCatalogo
 * @property {number} id
 * @property {string} nombre
 */

/**
 * Política de cancelación del catálogo: con al menos `diasSinCosto` días de
 * anticipación se devuelve el 100 %; después, `porcentajeDevolucion`.
 * @typedef {Object} PoliticaCancelacion
 * @property {number} id
 * @property {string} nombre
 * @property {string} descripcion
 * @property {number} diasSinCosto
 * @property {number} porcentajeDevolucion
 */

/**
 * @typedef {Object} ServicioAdicional
 * @property {number} [id] lo asigna la API al guardarlo
 * @property {string} nombre
 * @property {number} costo
 */

/**
 * @typedef {Object} Publicacion
 * @property {number} id
 * @property {string} titulo
 * @property {string | null} descripcion
 * @property {string | null} ciudad
 * @property {string | null} direccion
 * @property {OpcionCatalogo | null} tipo
 * @property {number | null} capacidad
 * @property {number | null} precioNoche
 * @property {OpcionCatalogo[]} servicios
 * @property {string | null} reglas
 * @property {string[]} imagenes en orden; la primera es la principal
 * @property {number | null} tarifaLimpieza
 * @property {'INMEDIATA' | 'APROBACION'} modoReserva
 * @property {PoliticaCancelacion | null} politicaCancelacion
 * @property {ServicioAdicional[]} serviciosAdicionales
 * @property {string} estado
 * @property {string[]} camposFaltantes lo que la API dice que falta para activarla
 */

/**
 * Datos del formulario tal como los escribe el anfitrión: cada campo es texto.
 * @typedef {Object} FormularioPublicacion
 * @property {string} titulo
 * @property {string} descripcion
 * @property {string} ciudad
 * @property {string} direccion
 * @property {string} tipo id del catálogo, o vacío
 * @property {string} capacidad
 * @property {string} precioNoche
 * @property {string} reglas
 */

/** @type {FormularioPublicacion} */
export const FORMULARIO_VACIO = {
  titulo: '', descripcion: '', ciudad: '', direccion: '', tipo: '', capacidad: '', precioNoche: '', reglas: '',
};

const ENTERO_POSITIVO = /^[1-9]\d*$/;
const PRECIO_VALIDO = /^\d{1,10}(\.\d{1,2})?$/;

/**
 * Valida solo el formato, con los mismos límites de la API; si los datos sirven
 * para activar la publicación lo decide la API.
 * @param {FormularioPublicacion} formulario
 * @returns {Partial<Record<keyof FormularioPublicacion, string>>} errores por campo
 */
export function validarFormulario(formulario) {
  const errores = {};
  if (!formulario.titulo.trim()) {
    errores.titulo = 'El título es obligatorio.';
  } else if (formulario.titulo.trim().length > 120) {
    errores.titulo = 'El título admite máximo 120 caracteres.';
  }
  if (formulario.capacidad.trim() && !ENTERO_POSITIVO.test(formulario.capacidad.trim())) {
    errores.capacidad = 'La capacidad debe ser un número entero mayor que cero.';
  }
  const precio = formulario.precioNoche.trim();
  if (precio && (!PRECIO_VALIDO.test(precio) || Number(precio) <= 0)) {
    errores.precioNoche = 'El precio por noche debe ser mayor que cero, con máximo 2 decimales.';
  }
  return errores;
}

/**
 * @param {Publicacion} publicacion
 * @returns {FormularioPublicacion}
 */
export function formularioDesde(publicacion) {
  const texto = (valor) => (valor === null || valor === undefined ? '' : String(valor));
  return {
    titulo: publicacion.titulo,
    descripcion: texto(publicacion.descripcion),
    ciudad: texto(publicacion.ciudad),
    direccion: texto(publicacion.direccion),
    tipo: texto(publicacion.tipo?.id),
    capacidad: texto(publicacion.capacidad),
    precioNoche: texto(publicacion.precioNoche),
    reglas: texto(publicacion.reglas),
  };
}

const SIN_SERVICIOS_NI_IMAGENES = Object.freeze({ servicios: [], imagenes: [] });

/**
 * Arma el cuerpo que espera la API. Los servicios y las imágenes se envían tal
 * como están, porque el PUT reemplaza la publicación completa y lo que no llegue se borra.
 * @param {FormularioPublicacion} formulario
 * @param {{servicios: OpcionCatalogo[], imagenes: string[]}} [actuales]
 */
export function solicitudDesde(formulario, actuales = SIN_SERVICIOS_NI_IMAGENES) {
  const opcional = (valor) => valor.trim() || null;
  const numero = (valor) => (valor.trim() ? Number(valor.trim()) : null);
  return {
    titulo: formulario.titulo.trim(),
    descripcion: opcional(formulario.descripcion),
    ciudad: opcional(formulario.ciudad),
    direccion: opcional(formulario.direccion),
    tipo: numero(formulario.tipo),
    capacidad: numero(formulario.capacidad),
    precioNoche: numero(formulario.precioNoche),
    reglas: opcional(formulario.reglas),
    servicios: actuales.servicios.map((servicio) => servicio.id),
    imagenes: actuales.imagenes,
  };
}

export const MAXIMO_IMAGENES = 10;

/**
 * Mismo formato que exige la API para cada imagen: URL http o https de hasta 500 caracteres.
 * @param {string} url
 * @param {string[]} actuales imágenes que ya tiene la lista
 * @returns {string | undefined} mensaje de error, o nada si se puede agregar
 */
export function errorDeImagen(url, actuales) {
  const limpia = url.trim();
  if (actuales.length >= MAXIMO_IMAGENES) {
    return `Se admiten máximo ${MAXIMO_IMAGENES} imágenes.`;
  }
  if (!limpia) {
    return 'Escribe la URL de la imagen.';
  }
  if (limpia.length > 500) {
    return 'Cada URL admite máximo 500 caracteres.';
  }
  try {
    const { protocol } = new URL(limpia);
    return protocol === 'http:' || protocol === 'https:' ? undefined : 'La imagen debe ser una URL http o https.';
  } catch {
    return 'La imagen debe ser una URL http o https.';
  }
}

/**
 * Condiciones de reserva tal como las edita el anfitrión: la tarifa y la
 * política son texto, y los servicios adicionales ya pasaron la validación.
 * @typedef {Object} FormularioConfiguracion
 * @property {string} tarifaLimpieza
 * @property {'INMEDIATA' | 'APROBACION'} modoReserva
 * @property {string} politica id del catálogo, o vacío
 * @property {ServicioAdicional[]} serviciosAdicionales
 */

/**
 * @param {Publicacion} publicacion
 * @returns {FormularioConfiguracion}
 */
export function configuracionDesde(publicacion) {
  return {
    tarifaLimpieza: publicacion.tarifaLimpieza === null ? '' : String(publicacion.tarifaLimpieza),
    modoReserva: publicacion.modoReserva,
    politica: publicacion.politicaCancelacion === null ? '' : String(publicacion.politicaCancelacion.id),
    serviciosAdicionales: publicacion.serviciosAdicionales.map(({ nombre, costo }) => ({ nombre, costo })),
  };
}

/**
 * Mismo formato que exige la API: cero o más, con máximo 2 decimales. Vacía es válida.
 * @param {string} tarifa
 * @returns {string | undefined} mensaje de error, o nada si sirve
 */
export function errorDeTarifa(tarifa) {
  const limpia = tarifa.trim();
  return limpia && !PRECIO_VALIDO.test(limpia)
    ? 'La tarifa de limpieza debe ser cero o más, con máximo 2 decimales.'
    : undefined;
}

/**
 * Valida el formato de un servicio adicional antes de agregarlo a la lista.
 * Si el nombre ya existe lo decide la API.
 * @param {string} nombre
 * @param {string} costo
 * @returns {{nombre?: string, costo?: string}} errores por campo; vacío si se puede agregar
 */
export function erroresDeServicioAdicional(nombre, costo) {
  const errores = {};
  const nombreLimpio = nombre.trim();
  if (!nombreLimpio) {
    errores.nombre = 'Escribe el nombre del servicio adicional.';
  } else if (nombreLimpio.length > 80) {
    errores.nombre = 'El nombre admite máximo 80 caracteres.';
  }
  const costoLimpio = costo.trim();
  if (!PRECIO_VALIDO.test(costoLimpio) || Number(costoLimpio) <= 0) {
    errores.costo = 'El costo debe ser mayor que cero, con máximo 2 decimales.';
  }
  return errores;
}

/**
 * Arma el cuerpo que espera la API. El PUT reemplaza todas las condiciones, así
 * que lo vacío se envía como nulo.
 * @param {FormularioConfiguracion} configuracion
 */
export function solicitudConfiguracionDesde(configuracion) {
  const tarifa = configuracion.tarifaLimpieza.trim();
  return {
    tarifaLimpieza: tarifa ? Number(tarifa) : null,
    modoReserva: configuracion.modoReserva,
    politicaCancelacion: configuracion.politica ? Number(configuracion.politica) : null,
    serviciosAdicionales: configuracion.serviciosAdicionales,
  };
}

const NOMBRE_ESTADO = {
  BORRADOR: 'Borrador',
};

/**
 * @param {string} estado
 * @returns {string} el estado como se le muestra al anfitrión
 */
export function nombreDeEstado(estado) {
  return NOMBRE_ESTADO[estado] ?? estado;
}

/**
 * @param {Object} respuesta publicación tal como la devuelve la API
 * @returns {Publicacion}
 */
export function publicacionDesdeApi(respuesta) {
  return {
    id: respuesta.id,
    titulo: respuesta.titulo,
    descripcion: respuesta.descripcion ?? null,
    ciudad: respuesta.ciudad ?? null,
    direccion: respuesta.direccion ?? null,
    tipo: respuesta.tipo ?? null,
    capacidad: respuesta.capacidad ?? null,
    precioNoche: respuesta.precioNoche ?? null,
    servicios: respuesta.servicios ?? [],
    reglas: respuesta.reglas ?? null,
    imagenes: respuesta.imagenes ?? [],
    tarifaLimpieza: respuesta.tarifaLimpieza ?? null,
    modoReserva: respuesta.modoReserva ?? 'INMEDIATA',
    politicaCancelacion: respuesta.politicaCancelacion ?? null,
    serviciosAdicionales: respuesta.serviciosAdicionales ?? [],
    estado: respuesta.estado,
    camposFaltantes: respuesta.camposFaltantes ?? [],
  };
}
