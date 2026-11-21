import PropTypes from 'prop-types';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { agruparPorCiudad } from '../../modules/busqueda/models/agruparPorCiudad.js';
import { consultarDestacados } from '../../modules/busqueda/services/inicioService.js';
import BuscadorInicio from './BuscadorInicio.jsx';
import estilos from './InicioHuespedPage.module.css';

const FORMATO_PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const RUTA_BUSQUEDA = '/huesped/busqueda';
const LIMITE_INICIO = 60;
const TARJETAS_POR_CIUDAD = 4;

function TarjetaAlojamiento({ alojamiento }) {
  return (
    <li>
      <Link to={`${RUTA_BUSQUEDA}/${alojamiento.id}`} className={estilos.tarjeta}>
        {alojamiento.imagenPrincipal ? (
          <img src={alojamiento.imagenPrincipal} alt={`Foto de ${alojamiento.titulo}`} className={estilos.foto} />
        ) : (
          <div className={estilos.sinFoto} aria-hidden="true" />
        )}
        <p className={estilos.tipo}>{alojamiento.tipo ?? 'Alojamiento'}</p>
        <p className={estilos.titulo}>{alojamiento.titulo}</p>
        <p className={estilos.precio}>
          {alojamiento.precioNoche != null ? `${FORMATO_PESOS.format(alojamiento.precioNoche)} por noche` : ''}
        </p>
      </Link>
    </li>
  );
}

TarjetaAlojamiento.propTypes = {
  alojamiento: PropTypes.shape({
    id: PropTypes.number.isRequired,
    titulo: PropTypes.string.isRequired,
    tipo: PropTypes.string,
    precioNoche: PropTypes.number,
    imagenPrincipal: PropTypes.string,
  }).isRequired,
};

/**
 * Ventana principal del huésped: saludo, buscador y los alojamientos publicados agrupados por ciudad.
 * Buscar lleva a la página de búsqueda con los criterios ya cargados.
 */
export default function InicioHuespedPage({ nombre }) {
  const navegar = useNavigate();
  const [alojamientos, setAlojamientos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let vigente = true;
    consultarDestacados(LIMITE_INICIO)
      .then((lista) => {
        if (vigente) setAlojamientos(lista);
      })
      .catch((falla) => {
        if (vigente) setError(falla.message);
      })
      .finally(() => {
        if (vigente) setCargando(false);
      });
    return () => {
      vigente = false;
    };
  }, []);

  function buscar(criterios) {
    navegar(`${RUTA_BUSQUEDA}?${new URLSearchParams(criterios)}`);
  }

  return (
    <main>
      <section className={estilos.banda}>
        <div className={estilos.interior}>
          <p className={estilos.eyebrow}>Encuentra tu próxima estancia</p>
          <h1 className={estilos.saludo}>Hola, {nombre}</h1>
          <BuscadorInicio alBuscar={buscar} anchoCompleto />
        </div>
      </section>

      <div className={estilos.contenido}>
        {cargando && <p className={estilos.aviso}>Cargando alojamientos…</p>}
        {error && <p role="alert" className={estilos.error}>{error}</p>}
        {!cargando && !error && alojamientos.length === 0 && (
          <p className={estilos.aviso}>Todavía no hay alojamientos publicados.</p>
        )}
        {agruparPorCiudad(alojamientos).map((grupo) => (
          <section key={grupo.ciudad.toLowerCase()} aria-labelledby={`ciudad-${grupo.ciudad}`}>
            <div className={estilos.encabezadoCiudad}>
              <h2 id={`ciudad-${grupo.ciudad}`} className={estilos.ciudad}>{grupo.ciudad}</h2>
              <Link to={`${RUTA_BUSQUEDA}?${new URLSearchParams({ ciudad: grupo.ciudad })}`} className={estilos.verTodos}
                aria-label={`Ver todos los alojamientos de ${grupo.ciudad}`}>
                Ver todos
              </Link>
            </div>
            <ul className={estilos.rejilla}>
              {grupo.alojamientos.slice(0, TARJETAS_POR_CIUDAD).map((alojamiento) => (
                <TarjetaAlojamiento key={alojamiento.id} alojamiento={alojamiento} />
              ))}
            </ul>
          </section>
        ))}
      </div>
    </main>
  );
}

InicioHuespedPage.propTypes = {
  nombre: PropTypes.string.isRequired,
};
