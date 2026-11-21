import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { listarServicios, listarTiposAlojamiento } from '../../alojamientos/services/alojamientosService.js';
import { buscarAlojamientos } from '../services/busquedaService.js';
import { guardarUltimaBusqueda, leerUltimaBusqueda } from '../services/ultimaBusqueda.js';
import estilos from './BusquedaPage.module.css';

const MENSAJE_ERROR = 'No pudimos buscar alojamientos. Intenta de nuevo.';
const FORMATO_PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

// Solo se valida que el formulario esté completo: que la salida sea posterior a la
// llegada lo decide la API, y su mensaje se muestra tal cual.
function erroresDeFormulario({ ciudad, llegada, salida }) {
  const errores = {};
  if (!ciudad.trim()) errores.ciudad = 'Indica la ciudad.';
  if (!llegada) errores.llegada = 'Indica la fecha de llegada.';
  if (!salida) errores.salida = 'Indica la fecha de salida.';
  return errores;
}

const CRITERIOS_INICIALES = {
  ciudad: '', llegada: '', salida: '', capacidad: '', tipo: '', precioMin: '', precioMax: '', servicios: [],
};

const CAMPOS_DE_LA_DIRECCION = ['ciudad', 'llegada', 'salida', 'capacidad'];

// Con criterios en la dirección (el inicio del huésped, "Ver todos") mandan ellos, y las fechas que falten
// se toman de la última búsqueda. Sin nada en la dirección se recupera la última búsqueda completa.
function criteriosIniciales(consulta) {
  const recordados = leerUltimaBusqueda();
  if (!CAMPOS_DE_LA_DIRECCION.some((campo) => consulta.has(campo))) {
    return { ...CRITERIOS_INICIALES, ...recordados };
  }
  return {
    ...CRITERIOS_INICIALES,
    ciudad: consulta.get('ciudad') ?? '',
    llegada: consulta.get('llegada') ?? recordados?.llegada ?? '',
    salida: consulta.get('salida') ?? recordados?.salida ?? '',
    capacidad: consulta.get('capacidad') ?? '',
  };
}

function IconoLupa() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true" fill="none" stroke="currentColor"
      strokeWidth="2.4" strokeLinecap="round">
      <circle cx="11" cy="11" r="6.5" />
      <path d="M16 16l4.5 4.5" />
    </svg>
  );
}

export default function BusquedaPage() {
  const [consulta] = useSearchParams();
  const [criterios, setCriterios] = useState(() => criteriosIniciales(consulta));
  const [tipos, setTipos] = useState([]);
  const [servicios, setServicios] = useState([]);
  const [errores, setErrores] = useState({});
  const [resultados, setResultados] = useState(null);
  // Las fechas con que se obtuvieron los resultados: viajan al detalle para precargar la cotización.
  const [fechasBuscadas, setFechasBuscadas] = useState({ llegada: '', salida: '' });
  const [errorBusqueda, setErrorBusqueda] = useState(null);
  const [buscando, setBuscando] = useState(false);
  // Cada búsqueda invalida las anteriores: una respuesta tardía no debe pisar a la más reciente.
  const solicitud = useRef(0);

  // Los catálogos solo alimentan los filtros: si no cargan, la búsqueda por ciudad y fechas sigue funcionando.
  useEffect(() => {
    listarTiposAlojamiento().then(setTipos).catch(() => setTipos([]));
    listarServicios().then(setServicios).catch(() => setServicios([]));
  }, []);

  async function ejecutarBusqueda(datos) {
    const invalidos = erroresDeFormulario(datos);
    setErrores(invalidos);
    setErrorBusqueda(null);
    if (Object.keys(invalidos).length > 0) return;
    solicitud.current += 1;
    const actual = solicitud.current;
    setBuscando(true);
    try {
      const encontrados = await buscarAlojamientos({ ...datos, ciudad: datos.ciudad.trim() });
      if (actual !== solicitud.current) return;
      setResultados(encontrados);
      setFechasBuscadas({ llegada: datos.llegada, salida: datos.salida });
      guardarUltimaBusqueda(datos);
    } catch (error) {
      if (actual !== solicitud.current) return;
      setResultados(null);
      setErrorBusqueda(error.estado === 400 ? error.message : MENSAJE_ERROR);
    } finally {
      if (actual === solicitud.current) setBuscando(false);
    }
  }

  // Con ciudad y fechas (de la dirección o de la última búsqueda), la búsqueda arranca sola al entrar.
  useEffect(() => {
    if (criterios.ciudad && criterios.llegada && criterios.salida) ejecutarBusqueda(criterios);
    // Solo al entrar: después la búsqueda la gobierna el formulario.
  }, []);

  function cambiar(evento) {
    const { name, value } = evento.target;
    setCriterios((actuales) => ({ ...actuales, [name]: value }));
  }

  // Los filtros se aplican al instante, pero solo una vez que hay una búsqueda de la que filtrar.
  function cambiarFiltro(evento) {
    const { name, value } = evento.target;
    const nuevos = { ...criterios, [name]: value };
    setCriterios(nuevos);
    if (resultados) ejecutarBusqueda(nuevos);
  }

  function alternarServicio(id) {
    const nuevos = {
      ...criterios,
      servicios: criterios.servicios.includes(id)
        ? criterios.servicios.filter((actual) => actual !== id)
        : [...criterios.servicios, id],
    };
    setCriterios(nuevos);
    if (resultados) ejecutarBusqueda(nuevos);
  }

  // Los precios y el mínimo de huéspedes se escriben con calma: se aplican al salir del campo.
  function aplicarAlSalir() {
    if (resultados) ejecutarBusqueda(criterios);
  }

  function limpiarFiltros() {
    const nuevos = {
      ...CRITERIOS_INICIALES, ciudad: criterios.ciudad, llegada: criterios.llegada, salida: criterios.salida,
    };
    setCriterios(nuevos);
    if (resultados) ejecutarBusqueda(nuevos);
  }

  function buscar(evento) {
    evento.preventDefault();
    ejecutarBusqueda(criterios);
  }

  const mensajesDeCampo = ['ciudad', 'llegada', 'salida'].filter((campo) => errores[campo]);

  return (
    <main>
      <section className={estilos.banda}>
        <div className={estilos.interior}>
          <h1 className={estilos.titulo}>Buscar alojamiento</h1>
          <form className={estilos.buscador} onSubmit={buscar} noValidate aria-label="Buscar alojamientos">
            <div className={estilos.campo}>
              <label htmlFor="ciudad">Ciudad</label>
              <input id="ciudad" name="ciudad" value={criterios.ciudad} onChange={cambiar} placeholder="Explora destinos"
                autoComplete="off" aria-invalid={errores.ciudad ? true : undefined}
                aria-describedby={errores.ciudad ? 'ciudad-error' : undefined} />
            </div>
            <div className={estilos.campo}>
              <label htmlFor="llegada">Llegada</label>
              <input id="llegada" name="llegada" type="date" value={criterios.llegada} onChange={cambiar}
                aria-invalid={errores.llegada ? true : undefined}
                aria-describedby={errores.llegada ? 'llegada-error' : undefined} />
            </div>
            <div className={estilos.campo}>
              <label htmlFor="salida">Salida</label>
              <input id="salida" name="salida" type="date" value={criterios.salida} onChange={cambiar}
                aria-invalid={errores.salida ? true : undefined}
                aria-describedby={errores.salida ? 'salida-error' : undefined} />
            </div>
            <button type="submit" className={estilos.buscar} disabled={buscando} aria-label="Buscar">
              <IconoLupa />
            </button>
          </form>
          {mensajesDeCampo.length > 0 && (
            <div className={estilos.erroresBusqueda}>
              {mensajesDeCampo.map((campo) => (
                <p key={campo} id={`${campo}-error`}>{errores[campo]}</p>
              ))}
            </div>
          )}
          {buscando && <p className={estilos.estado} role="status">Buscando…</p>}
        </div>
      </section>

      <div className={estilos.contenido}>
        <aside className={estilos.filtros} aria-label="Filtros">
          <div className={estilos.encabezadoFiltros}>
            <h2 className={estilos.tituloFiltros}>Filtros</h2>
            <button type="button" className={estilos.limpiar} onClick={limpiarFiltros}>Limpiar filtros</button>
          </div>

          <fieldset className={estilos.grupo}>
            <legend>Precio por noche</legend>
            <div className={estilos.rango}>
              <div className={estilos.campoFiltro}>
                <label htmlFor="precioMin">Precio mínimo por noche</label>
                <input id="precioMin" name="precioMin" type="number" min="0" placeholder="Mín." value={criterios.precioMin}
                  onChange={cambiar} onBlur={aplicarAlSalir} />
              </div>
              <div className={estilos.campoFiltro}>
                <label htmlFor="precioMax">Precio máximo por noche</label>
                <input id="precioMax" name="precioMax" type="number" min="0" placeholder="Máx." value={criterios.precioMax}
                  onChange={cambiar} onBlur={aplicarAlSalir} />
              </div>
            </div>
          </fieldset>

          <div className={estilos.campoFiltro}>
            <label htmlFor="capacidad">Huéspedes (mínimo)</label>
            <input id="capacidad" name="capacidad" type="number" min="1" value={criterios.capacidad}
              onChange={cambiar} onBlur={aplicarAlSalir} />
          </div>

          <div className={estilos.campoFiltro}>
            <label htmlFor="tipo">Tipo de alojamiento</label>
            <select id="tipo" name="tipo" value={criterios.tipo} onChange={cambiarFiltro}>
              <option value="">Todos</option>
              {tipos.map((tipo) => <option key={tipo.id} value={tipo.nombre}>{tipo.nombre}</option>)}
            </select>
          </div>

          {servicios.length > 0 && (
            <fieldset className={estilos.grupo}>
              <legend>Servicios</legend>
              {servicios.map((servicio) => (
                <label key={servicio.id} className={estilos.servicio}>
                  <input type="checkbox" checked={criterios.servicios.includes(servicio.id)}
                    onChange={() => alternarServicio(servicio.id)} />
                  {servicio.nombre}
                </label>
              ))}
            </fieldset>
          )}
        </aside>

        <section className={estilos.resultados} aria-label="Resultados">
          {errorBusqueda && <p role="alert" className={estilos.error}>{errorBusqueda}</p>}
          {resultados === null && !errorBusqueda && (
            <p className={estilos.vacio}>Elige una ciudad y las fechas de tu estancia para ver alojamientos disponibles.</p>
          )}
          {resultados?.length === 0 && (
            <p className={estilos.vacio}>
              No encontramos alojamientos disponibles en esas fechas. Prueba ampliando el rango de fechas.
            </p>
          )}
          {resultados?.length > 0 && (
            <>
              <p className={estilos.conteo}>
                {resultados.length === 1 ? '1 alojamiento disponible' : `${resultados.length} alojamientos disponibles`}
              </p>
              <ul className={estilos.lista}>
                {resultados.map((resultado) => (
                  <li key={resultado.id} className={estilos.fila}>
                    {resultado.imagenPrincipal ? (
                      <img src={resultado.imagenPrincipal} alt={`Foto de ${resultado.titulo}`} className={estilos.imagen} />
                    ) : (
                      <div className={estilos.sinImagen} aria-hidden="true" />
                    )}
                    <div className={estilos.info}>
                      <h2 className={estilos.nombre}>
                        <Link to={`/huesped/busqueda/${resultado.id}?${new URLSearchParams(fechasBuscadas)}`}>
                          {resultado.titulo}
                        </Link>
                      </h2>
                      <p className={estilos.detalle}>
                        {[resultado.tipo, resultado.ciudad].filter(Boolean).join(' · ')}
                        {resultado.capacidad != null && ` · Hasta ${resultado.capacidad} huéspedes`}
                      </p>
                      <Link to={`/huesped/publicaciones/${resultado.id}/calendario`} className={estilos.calendario}>
                        Ver calendario
                      </Link>
                    </div>
                    <div className={estilos.cierre}>
                      {resultado.precioNoche != null && (
                        <p className={estilos.precio}>{FORMATO_PESOS.format(resultado.precioNoche)} por noche</p>
                      )}
                      <Link to={`/huesped/busqueda/${resultado.id}?${new URLSearchParams(fechasBuscadas)}`}
                        className={estilos.irDetalle} aria-hidden="true" tabIndex={-1}>
                        →
                      </Link>
                    </div>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>
      </div>
    </main>
  );
}
