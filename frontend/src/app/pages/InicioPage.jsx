import PropTypes from 'prop-types';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useSesion } from '../../shared/sesion/useSesion.js';
import { buscarAlojamientos } from '../../modules/busqueda/services/busquedaService.js';
import { consultarDestacados } from '../../modules/busqueda/services/inicioService.js';
import BuscadorInicio from './BuscadorInicio.jsx';
import portada from '../../assets/inicio/portada.png';
import iconoIr from '../../assets/inicio/icono-ir.svg';
import estilos from './InicioPage.module.css';

const FORMATO_PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const RUTA_BUSQUEDA = '/huesped/busqueda';
const AVANCE_CARRUSEL = 560;
const LIMITE_INICIO = 60;

function Chevron({ haciaAtras }) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none" stroke="currentColor"
      strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d={haciaAtras ? 'M15 5l-7 7 7 7' : 'M9 5l7 7-7 7'} />
    </svg>
  );
}

Chevron.propTypes = { haciaAtras: PropTypes.bool };

const PROPTYPES_ALOJAMIENTO = PropTypes.shape({
  id: PropTypes.number.isRequired,
  titulo: PropTypes.string.isRequired,
  ciudad: PropTypes.string,
  tipo: PropTypes.string,
  capacidad: PropTypes.number,
  precioNoche: PropTypes.number,
  imagenPrincipal: PropTypes.string,
});

function TarjetaAlojamiento({ alojamiento, alAbrir }) {
  return (
    <li className={estilos.tarjeta}>
      <button type="button" className={estilos.botonTarjeta} onClick={() => alAbrir(alojamiento)}>
        {alojamiento.imagenPrincipal ? (
          <img src={alojamiento.imagenPrincipal} alt={`Foto de ${alojamiento.titulo}`} className={estilos.foto} />
        ) : (
          <div className={estilos.sinFoto}>Sin foto</div>
        )}
        <div className={estilos.texto}>
          <p className={estilos.tipo}>{alojamiento.tipo ?? alojamiento.titulo}</p>
          <p className={estilos.detalle}>{alojamiento.titulo} · {alojamiento.ciudad}</p>
        </div>
        <div className={estilos.pie}>
          {alojamiento.precioNoche != null ? (
            <p className={estilos.precio}>{FORMATO_PESOS.format(alojamiento.precioNoche)} por noche</p>
          ) : (
            <span />
          )}
          <img src={iconoIr} alt="" className={estilos.iconoIr} />
        </div>
      </button>
    </li>
  );
}

TarjetaAlojamiento.propTypes = {
  alojamiento: PROPTYPES_ALOJAMIENTO.isRequired,
  alAbrir: PropTypes.func.isRequired,
};

function FilaCiudad({ ciudad, alojamientos, alAbrir }) {
  const carrusel = useRef(null);
  const idTitulo = `titulo-${ciudad}`;

  function mover(sentido) {
    carrusel.current?.scrollBy?.({ left: sentido * AVANCE_CARRUSEL, behavior: 'smooth' });
  }

  return (
    <section aria-labelledby={idTitulo}>
      <div className={estilos.encabezadoSeccion}>
        <h2 id={idTitulo} className={estilos.subtitulo}>Alojamientos en {ciudad}</h2>
      </div>
      <div className={estilos.carrusel}>
        <button type="button" className={estilos.flecha} onClick={() => mover(-1)}
          aria-label={`Ver anteriores en ${ciudad}`}>
          <Chevron haciaAtras />
        </button>
        <ul ref={carrusel} className={estilos.tarjetas}>
          {alojamientos.map((alojamiento) => (
            <TarjetaAlojamiento key={alojamiento.id} alojamiento={alojamiento} alAbrir={alAbrir} />
          ))}
        </ul>
        <button type="button" className={estilos.flecha} onClick={() => mover(1)}
          aria-label={`Ver más alojamientos en ${ciudad}`}>
          <Chevron />
        </button>
      </div>
    </section>
  );
}

FilaCiudad.propTypes = {
  ciudad: PropTypes.string.isRequired,
  alojamientos: PropTypes.arrayOf(PROPTYPES_ALOJAMIENTO).isRequired,
  alAbrir: PropTypes.func.isRequired,
};

/** Resumen del alojamiento: quien no tiene sesión pasa por el inicio de sesión; el huésped sigue al detalle. */
function ResumenAlojamiento({ alojamiento, fechas, alCerrar }) {
  const sesion = useSesion();
  const navegar = useNavigate();
  // Si viene de una búsqueda, las fechas viajan al detalle para precargar la cotización.
  const destino = `${RUTA_BUSQUEDA}/${alojamiento.id}${fechas ? `?${new URLSearchParams(fechas)}` : ''}`;
  const esAnfitrionOAdmin = sesion && sesion.usuario.rol !== 'HUESPED';

  useEffect(() => {
    function alTeclear(evento) {
      if (evento.key === 'Escape') alCerrar();
    }
    document.addEventListener('keydown', alTeclear);
    return () => document.removeEventListener('keydown', alTeclear);
  }, [alCerrar]);

  function reservar() {
    if (!sesion) {
      navegar('/inicio-sesion', { state: { destino } });
      return;
    }
    navegar(destino);
  }

  return (
    <div className={estilos.fondoResumen} onClick={alCerrar} role="presentation">
      <div className={estilos.resumen} role="dialog" aria-modal="true" aria-labelledby="titulo-resumen"
        onClick={(evento) => evento.stopPropagation()}>
        {alojamiento.imagenPrincipal && (
          <img src={alojamiento.imagenPrincipal} alt={`Foto de ${alojamiento.titulo}`} className={estilos.fotoResumen} />
        )}
        <div className={estilos.cuerpoResumen}>
          <p className={estilos.tipoResumen}>{alojamiento.tipo ?? 'Alojamiento'} · {alojamiento.ciudad}</p>
          <h2 id="titulo-resumen" className={estilos.tituloResumen}>{alojamiento.titulo}</h2>
          {alojamiento.capacidad != null && (
            <p className={estilos.datoResumen}>Hasta {alojamiento.capacidad} huéspedes</p>
          )}
          {alojamiento.precioNoche != null && (
            <p className={estilos.precioResumen}>{FORMATO_PESOS.format(alojamiento.precioNoche)} <span>por noche</span></p>
          )}
          {esAnfitrionOAdmin && (
            <p className={estilos.datoResumen}>Solo las cuentas de huésped pueden reservar.</p>
          )}
          <div className={estilos.accionesResumen}>
            <button type="button" className={estilos.cerrar} onClick={alCerrar}>Cerrar</button>
            {!esAnfitrionOAdmin && (
              <button type="button" className={estilos.reservarResumen} onClick={reservar}>Reservar</button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

ResumenAlojamiento.propTypes = {
  alojamiento: PROPTYPES_ALOJAMIENTO.isRequired,
  fechas: PropTypes.shape({ llegada: PropTypes.string, salida: PropTypes.string }),
  alCerrar: PropTypes.func.isRequired,
};

function ResultadosBusqueda({ busqueda, alAbrir, alQuitar }) {
  const { criterios, resultados } = busqueda;
  return (
    <section aria-labelledby="titulo-resultados">
      <div className={estilos.encabezadoSeccion}>
        <h2 id="titulo-resultados" className={estilos.subtitulo}>Resultados en {criterios.ciudad}</h2>
        <button type="button" className={estilos.quitar} onClick={alQuitar}>Quitar búsqueda</button>
      </div>
      {resultados.length === 0 ? (
        <p className={estilos.aviso}>No encontramos alojamientos disponibles con esos datos. Prueba con otras fechas u otra ciudad.</p>
      ) : (
        <ul className={estilos.rejilla}>
          {resultados.map((alojamiento) => (
            <TarjetaAlojamiento key={alojamiento.id} alojamiento={alojamiento} alAbrir={alAbrir} />
          ))}
        </ul>
      )}
    </section>
  );
}

ResultadosBusqueda.propTypes = {
  busqueda: PropTypes.shape({
    criterios: PropTypes.shape({ ciudad: PropTypes.string, llegada: PropTypes.string, salida: PropTypes.string }),
    resultados: PropTypes.arrayOf(PROPTYPES_ALOJAMIENTO),
  }).isRequired,
  alAbrir: PropTypes.func.isRequired,
  alQuitar: PropTypes.func.isRequired,
};

/** Agrupa por ciudad sin distinguir mayúsculas ni espacios; las ciudades salen en el orden en que aparecen. */
function agruparPorCiudad(alojamientos) {
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

export default function InicioPage() {
  const sesion = useSesion();
  const [alojamientos, setAlojamientos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [seleccionado, setSeleccionado] = useState(null);
  const [busqueda, setBusqueda] = useState(null);
  const [buscando, setBuscando] = useState(false);
  const [errorBusqueda, setErrorBusqueda] = useState('');

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

  async function buscar(criterios) {
    setBuscando(true);
    setErrorBusqueda('');
    try {
      setBusqueda({ criterios, resultados: await buscarAlojamientos(criterios) });
    } catch (falla) {
      setErrorBusqueda(falla.message);
    } finally {
      setBuscando(false);
    }
  }

  function abrir(alojamiento) {
    const fechas = busqueda ? { llegada: busqueda.criterios.llegada, salida: busqueda.criterios.salida } : null;
    setSeleccionado({ alojamiento, fechas });
  }

  return (
    <main className={estilos.pagina}>
      <section className={estilos.portada} style={{ backgroundImage: `url(${portada})` }}>
        <h1 className={estilos.titulo}>Encuentra tu alojamiento</h1>
        <div className={estilos.acciones}>
          <Link to={RUTA_BUSQUEDA} className={estilos.reservar}>Reservar</Link>
          {!sesion && <Link to="/registro" className={estilos.crearCuenta}>Crear una cuenta</Link>}
        </div>
      </section>

      <BuscadorInicio alBuscar={buscar} buscando={buscando} />
      {errorBusqueda && <p role="alert" className={estilos.error}>{errorBusqueda}</p>}

      {busqueda ? (
        <ResultadosBusqueda busqueda={busqueda} alAbrir={abrir} alQuitar={() => setBusqueda(null)} />
      ) : (
        <>
          {cargando && <p className={estilos.aviso}>Cargando alojamientos…</p>}
          {error && <p role="alert" className={estilos.error}>{error}</p>}
          {!cargando && !error && alojamientos.length === 0 && (
            <p className={estilos.aviso}>Todavía no hay alojamientos publicados.</p>
          )}
          {agruparPorCiudad(alojamientos).map((grupo) => (
            <FilaCiudad key={grupo.ciudad.toLowerCase()} ciudad={grupo.ciudad} alojamientos={grupo.alojamientos}
              alAbrir={abrir} />
          ))}
        </>
      )}

      {seleccionado && (
        <ResumenAlojamiento alojamiento={seleccionado.alojamiento} fechas={seleccionado.fechas}
          alCerrar={() => setSeleccionado(null)} />
      )}
    </main>
  );
}
