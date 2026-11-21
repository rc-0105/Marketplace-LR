import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { formatearFecha, formatearPesos, nombreDeEstadoReserva } from '../models/reserva.js';
import { listarMisReservas } from '../services/reservaService.js';
import estilos from './MisReservasPage.module.css';

const MENSAJE_ERROR = 'No pudimos cargar tus reservas. Intenta de nuevo.';
const SIN_ALOJAMIENTO = 'Alojamiento no disponible';

function fechasDe(reserva) {
  const noches = reserva.noches === 1 ? '1 noche' : `${reserva.noches} noches`;
  return `${formatearFecha(reserva.llegada)} – ${formatearFecha(reserva.salida)} · ${noches}`;
}

function TarjetaReserva({ reserva }) {
  const titulo = reserva.titulo ?? SIN_ALOJAMIENTO;
  return (
    <li className={estilos.tarjeta}>
      {reserva.imagenPrincipal ? (
        <img src={reserva.imagenPrincipal} alt={`Foto de ${titulo}`} className={estilos.foto} />
      ) : (
        <div className={estilos.sinFoto}>Sin foto</div>
      )}
      <div className={estilos.texto}>
        <span className={`${estilos.estado} ${estilos[reserva.estado]}`}>{nombreDeEstadoReserva(reserva.estado)}</span>
        <h3 className={estilos.nombre}>{titulo}</h3>
        {reserva.ciudad && <p className={estilos.detalle}>{reserva.ciudad}</p>}
        <p className={estilos.detalle}>{fechasDe(reserva)}</p>
      </div>
      <div className={estilos.cierre}>
        <p className={estilos.total}>{formatearPesos(reserva.total)}</p>
        <Link to={`/huesped/reservas/${reserva.id}`} className={estilos.enlace} aria-label={`Ver detalle de ${titulo}`}>
          Ver detalle
        </Link>
      </div>
    </li>
  );
}

const resumenPropTypes = PropTypes.shape({
  id: PropTypes.number.isRequired,
  titulo: PropTypes.string,
  ciudad: PropTypes.string,
  imagenPrincipal: PropTypes.string,
  llegada: PropTypes.string.isRequired,
  salida: PropTypes.string.isRequired,
  noches: PropTypes.number.isRequired,
  estado: PropTypes.string.isRequired,
  total: PropTypes.number.isRequired,
});

TarjetaReserva.propTypes = {
  reserva: resumenPropTypes.isRequired,
};

function Seccion({ titulo, reservas, vacia }) {
  return (
    <section className={estilos.seccion} aria-label={titulo}>
      <h2 className={estilos.tituloSeccion}>{titulo}</h2>
      {reservas.length === 0 ? (
        <p className={estilos.nota}>{vacia}</p>
      ) : (
        <ul className={estilos.lista}>
          {reservas.map((reserva) => <TarjetaReserva key={reserva.id} reserva={reserva} />)}
        </ul>
      )}
    </section>
  );
}

Seccion.propTypes = {
  titulo: PropTypes.string.isRequired,
  reservas: PropTypes.arrayOf(resumenPropTypes).isRequired,
  vacia: PropTypes.string.isRequired,
};

/** Estado e historial de las reservas del huésped (SOF-35): próximas y pasadas, cada una con su detalle. */
export default function MisReservasPage() {
  const [reservas, setReservas] = useState(null);
  const [errorCarga, setErrorCarga] = useState(null);

  useEffect(() => {
    let vigente = true;
    listarMisReservas()
      .then((mias) => vigente && setReservas(mias))
      .catch(() => vigente && setErrorCarga(MENSAJE_ERROR));
    // Si la página se desmonta antes de la respuesta, no se toca el estado.
    return () => {
      vigente = false;
    };
  }, []);

  const sinNinguna = reservas && reservas.futuras.length === 0 && reservas.pasadas.length === 0;

  return (
    <main className={estilos.pagina}>
      <div>
        <h1 className={estilos.titulo}>Mis reservas</h1>
        <p className={estilos.subtitulo}>Sigue el estado de tus viajes próximos y revisa los anteriores.</p>
      </div>
      {errorCarga && <p role="alert" className={estilos.error}>{errorCarga}</p>}
      {!errorCarga && reservas === null && <p className={estilos.nota}>Cargando tus reservas…</p>}
      {sinNinguna && (
        <div className={estilos.vacio}>
          <p className={estilos.nota}>Todavía no tienes reservas.</p>
          <Link to="/huesped/busqueda" className={estilos.enlace}>Buscar alojamiento</Link>
        </div>
      )}
      {reservas && !sinNinguna && (
        <>
          <Seccion titulo="Próximas" reservas={reservas.futuras} vacia="No tienes reservas próximas." />
          <Seccion titulo="Pasadas" reservas={reservas.pasadas} vacia="No tienes reservas pasadas." />
        </>
      )}
    </main>
  );
}
