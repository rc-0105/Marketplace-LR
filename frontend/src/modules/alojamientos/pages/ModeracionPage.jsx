import PropTypes from 'prop-types';
import { useCallback, useEffect, useState } from 'react';
import { errorDeMotivo, nombreDeEstado } from '../models/publicacion.js';
import { bloquearPublicacion, buscarPublicacionesModerables } from '../services/alojamientosService.js';
import { formatearFecha, formatearPesos } from '../../reservas/models/reserva.js';
import {
  listarReservasEnRevision, resolverReservaEnRevision,
} from '../../reservas/services/reservaService.js';
import estilos from './ModeracionPage.module.css';

const MENSAJE_BUSQUEDA_FALLIDA = 'No pudimos buscar las publicaciones. Intenta de nuevo.';
const MENSAJE_CARGA_REVISION = 'No pudimos cargar las reservas en revisión. Intenta de nuevo.';
const MENSAJE_ACCION_FALLIDA = 'No pudimos guardar el cambio. Intenta de nuevo.';

// Solo las activas y las pausadas se pueden bloquear; la API vuelve a validarlo.
const BLOQUEABLES = ['ACTIVA', 'PAUSADA'];

// Un error de la API trae su detalle (409, 502…); cualquier otro fallo se explica con un mensaje genérico.
function mensajeDe(error) {
  return error.estado ? error.message : MENSAJE_ACCION_FALLIDA;
}

function Motivo({ id, etiqueta, valor, error, onChange }) {
  return (
    <div className={estilos.campo}>
      <label className={estilos.etiqueta} htmlFor={id}>{etiqueta}</label>
      <textarea id={id} className={estilos.control} rows={2} maxLength={500} value={valor}
        aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined}
        onChange={(evento) => onChange(evento.target.value)} />
      {error && <p id={`${id}-error`} className={estilos.errorCampo}>{error}</p>}
    </div>
  );
}

Motivo.propTypes = {
  id: PropTypes.string.isRequired,
  etiqueta: PropTypes.string.isRequired,
  valor: PropTypes.string.isRequired,
  error: PropTypes.string,
  onChange: PropTypes.func.isRequired,
};

export default function ModeracionPage() {
  const [texto, setTexto] = useState('');
  const [resultados, setResultados] = useState(null);
  const [errorBusqueda, setErrorBusqueda] = useState(null);
  const [enRevision, setEnRevision] = useState(null);
  const [errorRevision, setErrorRevision] = useState(null);
  // Estado por tarjeta, indexado por id: el motivo escrito, su error de formato y el error de la API.
  const [motivosBloqueo, setMotivosBloqueo] = useState({});
  const [erroresMotivoBloqueo, setErroresMotivoBloqueo] = useState({});
  const [erroresPublicacion, setErroresPublicacion] = useState({});
  const [motivosCancelacion, setMotivosCancelacion] = useState({});
  const [erroresMotivoCancelacion, setErroresMotivoCancelacion] = useState({});
  const [erroresReserva, setErroresReserva] = useState({});
  const [enCurso, setEnCurso] = useState(null);
  const [confirmacion, setConfirmacion] = useState(null);

  const cargarEnRevision = useCallback(async (vigente = () => true) => {
    try {
      const lista = await listarReservasEnRevision();
      if (vigente()) {
        setEnRevision(lista);
        setErrorRevision(null);
      }
    } catch {
      if (vigente()) {
        setErrorRevision(MENSAJE_CARGA_REVISION);
      }
    }
  }, []);

  useEffect(() => {
    let montada = true;
    cargarEnRevision(() => montada);
    // Si la página se desmonta antes de la respuesta, no se toca el estado.
    return () => {
      montada = false;
    };
  }, [cargarEnRevision]);

  async function buscar(evento) {
    evento.preventDefault();
    setErrorBusqueda(null);
    try {
      setResultados(await buscarPublicacionesModerables(texto));
    } catch {
      setErrorBusqueda(MENSAJE_BUSQUEDA_FALLIDA);
    }
  }

  async function bloquear(evento, publicacion) {
    evento.preventDefault();
    const motivo = motivosBloqueo[publicacion.id] ?? '';
    const error = errorDeMotivo(motivo);
    setErroresMotivoBloqueo((actuales) => ({ ...actuales, [publicacion.id]: error }));
    if (error) {
      return;
    }
    setEnCurso(`publicacion-${publicacion.id}`);
    setConfirmacion(null);
    setErroresPublicacion((actuales) => ({ ...actuales, [publicacion.id]: null }));
    try {
      const bloqueada = await bloquearPublicacion(publicacion.id, motivo.trim());
      setResultados((lista) => lista.map((otra) => (otra.id === publicacion.id
        ? { ...otra, estado: bloqueada.estado, motivoBloqueo: bloqueada.motivoBloqueo }
        : otra)));
      setConfirmacion(`«${publicacion.titulo}» quedó bloqueada.`);
      // Al bloquearla, sus reservas futuras confirmadas pasan a revisión manual.
      await cargarEnRevision();
    } catch (fallo) {
      setErroresPublicacion((actuales) => ({ ...actuales, [publicacion.id]: mensajeDe(fallo) }));
    } finally {
      setEnCurso(null);
    }
  }

  async function resolver(reserva, ejecutar, mensaje) {
    setEnCurso(`reserva-${reserva.id}`);
    setConfirmacion(null);
    setErroresReserva((actuales) => ({ ...actuales, [reserva.id]: null }));
    try {
      await ejecutar();
      setEnRevision((lista) => lista.filter((otra) => otra.id !== reserva.id));
      setConfirmacion(mensaje);
    } catch (fallo) {
      setErroresReserva((actuales) => ({ ...actuales, [reserva.id]: mensajeDe(fallo) }));
    } finally {
      setEnCurso(null);
    }
  }

  async function confirmar(reserva) {
    await resolver(reserva, () => resolverReservaEnRevision(reserva.id, 'CONFIRMAR'),
      `La reserva #${reserva.id} quedó confirmada.`);
  }

  async function cancelar(evento, reserva) {
    evento.preventDefault();
    const motivo = motivosCancelacion[reserva.id] ?? '';
    const error = errorDeMotivo(motivo);
    setErroresMotivoCancelacion((actuales) => ({ ...actuales, [reserva.id]: error }));
    if (error) {
      return;
    }
    await resolver(reserva, () => resolverReservaEnRevision(reserva.id, 'CANCELAR', motivo.trim()),
      `La reserva #${reserva.id} quedó cancelada y se devolvió el total al huésped.`);
  }

  function tarjetaPublicacion(publicacion) {
    const datos = [publicacion.ciudad, publicacion.anfitrion && `Anfitrión: ${publicacion.anfitrion}`]
      .filter(Boolean).join(' · ');
    const idMotivo = `motivo-bloqueo-${publicacion.id}`;
    return (
      <li key={publicacion.id} className={estilos.tarjeta}>
        <div className={estilos.cabeceraTarjeta}>
          <h3 className={estilos.nombre}>{publicacion.titulo}</h3>
          <span className={`${estilos.estado} ${estilos[publicacion.estado] ?? ''}`}>
            {nombreDeEstado(publicacion.estado)}
          </span>
        </div>
        {datos && <p className={estilos.detalle}>{datos}</p>}
        {publicacion.estado === 'BLOQUEADA' && publicacion.motivoBloqueo && (
          <p className={estilos.motivo}>Motivo del bloqueo: {publicacion.motivoBloqueo}</p>
        )}
        {erroresPublicacion[publicacion.id] && (
          <p role="alert" className={estilos.error}>{erroresPublicacion[publicacion.id]}</p>
        )}
        {BLOQUEABLES.includes(publicacion.estado) && (
          <form className={estilos.formulario} onSubmit={(evento) => bloquear(evento, publicacion)} noValidate>
            <Motivo id={idMotivo} etiqueta={`Motivo del bloqueo de ${publicacion.titulo}`}
              valor={motivosBloqueo[publicacion.id] ?? ''} error={erroresMotivoBloqueo[publicacion.id]}
              onChange={(valor) => setMotivosBloqueo((actuales) => ({ ...actuales, [publicacion.id]: valor }))} />
            <div className={estilos.acciones}>
              <button type="submit" className={estilos.peligro} disabled={enCurso === `publicacion-${publicacion.id}`}
                aria-label={`Bloquear ${publicacion.titulo}`}>
                Bloquear
              </button>
            </div>
          </form>
        )}
      </li>
    );
  }

  function tarjetaReserva(reserva) {
    const idMotivo = `motivo-cancelacion-${reserva.id}`;
    const ocupada = enCurso === `reserva-${reserva.id}`;
    return (
      <li key={reserva.id} className={estilos.tarjeta}>
        <h3 className={estilos.nombre}>{reserva.titulo}</h3>
        <p className={estilos.detalle}>
          {[`Reserva #${reserva.id}`, reserva.huesped && `Huésped: ${reserva.huesped}`].filter(Boolean).join(' · ')}
        </p>
        <p className={estilos.detalle}>
          {formatearFecha(reserva.llegada)} – {formatearFecha(reserva.salida)} · Total {formatearPesos(reserva.total)}
        </p>
        {reserva.motivoBloqueo && <p className={estilos.motivo}>Motivo del bloqueo: {reserva.motivoBloqueo}</p>}
        {erroresReserva[reserva.id] && <p role="alert" className={estilos.error}>{erroresReserva[reserva.id]}</p>}
        <form className={estilos.formulario} onSubmit={(evento) => cancelar(evento, reserva)} noValidate>
          <Motivo id={idMotivo} etiqueta={`Motivo de cancelación de la reserva #${reserva.id}`}
            valor={motivosCancelacion[reserva.id] ?? ''} error={erroresMotivoCancelacion[reserva.id]}
            onChange={(valor) => setMotivosCancelacion((actuales) => ({ ...actuales, [reserva.id]: valor }))} />
          <div className={estilos.acciones}>
            <button type="button" className={estilos.principal} disabled={ocupada}
              aria-label={`Confirmar reserva #${reserva.id}`} onClick={() => confirmar(reserva)}>
              Confirmar reserva
            </button>
            <button type="submit" className={estilos.peligro} disabled={ocupada}
              aria-label={`Cancelar con devolución la reserva #${reserva.id}`}>
              Cancelar con devolución
            </button>
          </div>
        </form>
      </li>
    );
  }

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Moderación</h1>
      <p className={estilos.nota}>
        Bloquea las publicaciones con información engañosa y decide qué pasa con sus reservas futuras.
      </p>
      <output className={confirmacion ? estilos.confirmacion : undefined}>{confirmacion}</output>

      <section className={estilos.seccion} aria-labelledby="bloquear-titulo">
        <h2 id="bloquear-titulo" className={estilos.subtitulo}>Bloquear publicaciones</h2>
        <form className={estilos.busqueda} onSubmit={buscar} role="search">
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="texto-busqueda">Buscar por título</label>
            <input id="texto-busqueda" type="search" className={estilos.control} value={texto} maxLength={120}
              onChange={(evento) => setTexto(evento.target.value)} />
          </div>
          <button type="submit" className={estilos.principal}>Buscar</button>
        </form>
        {errorBusqueda && <p role="alert" className={estilos.error}>{errorBusqueda}</p>}
        {resultados?.length === 0 && (
          <p className={estilos.nota}>No hay publicaciones activas, pausadas ni bloqueadas con ese título.</p>
        )}
        {resultados?.length > 0 && (
          <ul className={estilos.lista} aria-label="Resultados de la búsqueda">
            {resultados.map(tarjetaPublicacion)}
          </ul>
        )}
      </section>

      <section className={estilos.seccion} aria-labelledby="revision-titulo">
        <h2 id="revision-titulo" className={estilos.subtitulo}>Reservas en revisión</h2>
        <p className={estilos.nota}>
          Reservas futuras de publicaciones bloqueadas. Confírmalas o cancélalas con la devolución total al huésped.
        </p>
        {errorRevision && <p role="alert" className={estilos.error}>{errorRevision}</p>}
        {!errorRevision && enRevision === null && <p className={estilos.nota}>Cargando reservas en revisión…</p>}
        {enRevision?.length === 0 && <p className={estilos.nota}>No hay reservas en revisión.</p>}
        {enRevision?.length > 0 && (
          <ul className={estilos.lista} aria-label="Reservas en revisión">{enRevision.map(tarjetaReserva)}</ul>
        )}
      </section>
    </main>
  );
}
