import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listarServicios, listarTiposAlojamiento } from '../../alojamientos/services/alojamientosService.js';
import Campo from '../../../shared/components/Campo.jsx';
import { buscarAlojamientos } from '../services/busquedaService.js';
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

export default function BusquedaPage() {
  const [criterios, setCriterios] = useState(CRITERIOS_INICIALES);
  const [tipos, setTipos] = useState([]);
  const [servicios, setServicios] = useState([]);
  const [errores, setErrores] = useState({});
  const [resultados, setResultados] = useState(null);
  const [errorBusqueda, setErrorBusqueda] = useState(null);
  const [buscando, setBuscando] = useState(false);

  // Los catálogos solo alimentan los filtros: si no cargan, la búsqueda por ciudad y fechas sigue funcionando.
  useEffect(() => {
    listarTiposAlojamiento().then(setTipos).catch(() => setTipos([]));
    listarServicios().then(setServicios).catch(() => setServicios([]));
  }, []);

  function cambiar(evento) {
    const { name, value } = evento.target;
    setCriterios((actuales) => ({ ...actuales, [name]: value }));
  }

  function alternarServicio(id) {
    setCriterios((actuales) => ({
      ...actuales,
      servicios: actuales.servicios.includes(id)
        ? actuales.servicios.filter((actual) => actual !== id)
        : [...actuales.servicios, id],
    }));
  }

  function limpiarFiltros() {
    setCriterios((actuales) => ({
      ...CRITERIOS_INICIALES, ciudad: actuales.ciudad, llegada: actuales.llegada, salida: actuales.salida,
    }));
  }

  async function buscar(evento) {
    evento.preventDefault();
    const invalidos = erroresDeFormulario(criterios);
    setErrores(invalidos);
    setErrorBusqueda(null);
    if (Object.keys(invalidos).length > 0) return;
    setBuscando(true);
    try {
      setResultados(await buscarAlojamientos({ ...criterios, ciudad: criterios.ciudad.trim() }));
    } catch (error) {
      setResultados(null);
      setErrorBusqueda(error.estado === 400 ? error.message : MENSAJE_ERROR);
    } finally {
      setBuscando(false);
    }
  }

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Buscar alojamiento</h1>
      <form className={estilos.formulario} onSubmit={buscar} noValidate>
        <Campo id="ciudad" etiqueta="Ciudad" value={criterios.ciudad} onChange={cambiar} error={errores.ciudad} />
        <Campo id="llegada" etiqueta="Llegada" type="date" value={criterios.llegada} onChange={cambiar}
          error={errores.llegada} />
        <Campo id="salida" etiqueta="Salida" type="date" value={criterios.salida} onChange={cambiar}
          error={errores.salida} />
        <fieldset className={estilos.filtros}>
          <legend>Filtros</legend>
          <Campo id="capacidad" etiqueta="Huéspedes (mínimo)" type="number" min="1" value={criterios.capacidad}
            onChange={cambiar} />
          <div className={estilos.selector}>
            <label htmlFor="tipo">Tipo de alojamiento</label>
            <select id="tipo" name="tipo" value={criterios.tipo} onChange={cambiar}>
              <option value="">Todos</option>
              {tipos.map((tipo) => <option key={tipo.id} value={tipo.nombre}>{tipo.nombre}</option>)}
            </select>
          </div>
          <Campo id="precioMin" etiqueta="Precio mínimo por noche" type="number" min="0" value={criterios.precioMin}
            onChange={cambiar} />
          <Campo id="precioMax" etiqueta="Precio máximo por noche" type="number" min="0" value={criterios.precioMax}
            onChange={cambiar} />
          {servicios.length > 0 && (
            <fieldset className={estilos.servicios}>
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
          <button type="button" className={estilos.limpiar} onClick={limpiarFiltros}>Limpiar filtros</button>
        </fieldset>
        <button type="submit" className={estilos.buscar} disabled={buscando}>
          {buscando ? 'Buscando…' : 'Buscar'}
        </button>
      </form>
      {errorBusqueda && <p role="alert" className={estilos.error}>{errorBusqueda}</p>}
      {resultados?.length === 0 && (
        <p className={estilos.vacio}>
          No encontramos alojamientos disponibles en esas fechas. Prueba ampliando el rango de fechas.
        </p>
      )}
      {resultados?.length > 0 && (
        <ul className={estilos.lista}>
          {resultados.map((resultado) => (
            <li key={resultado.id} className={estilos.tarjeta}>
              {resultado.imagenPrincipal && (
                <img src={resultado.imagenPrincipal} alt={`Foto de ${resultado.titulo}`} className={estilos.imagen} />
              )}
              <h2 className={estilos.nombre}>
                <Link to={`/huesped/busqueda/${resultado.id}`}>{resultado.titulo}</Link>
              </h2>
              <p className={estilos.detalle}>
                {[resultado.tipo, resultado.ciudad].filter(Boolean).join(' · ')}
                {resultado.capacidad != null && ` · Hasta ${resultado.capacidad} huéspedes`}
              </p>
              {resultado.precioNoche != null && (
                <p className={estilos.precio}>{FORMATO_PESOS.format(resultado.precioNoche)} por noche</p>
              )}
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
