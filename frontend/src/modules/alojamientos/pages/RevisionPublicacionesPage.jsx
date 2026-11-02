import { useEffect, useState } from 'react';
import { errorDeObservaciones } from '../models/publicacion.js';
import {
  aprobarPublicacion, devolverPublicacion, listarPendientesDeRevision,
} from '../services/alojamientosService.js';
import estilos from './RevisionPublicacionesPage.module.css';

const MENSAJE_ERROR = 'No pudimos cargar las publicaciones pendientes. Intenta de nuevo.';
const MENSAJE_REVISION_FALLIDA = 'No pudimos guardar la revisión. Intenta de nuevo.';

// Las fechas del negocio se muestran en hora de Colombia, sin importar dónde esté el navegador.
const FORMATO_FECHA = { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'America/Bogota' };

function datosDe(publicacion) {
  const partes = [
    publicacion.tipo?.nombre,
    publicacion.ciudad,
    publicacion.capacidad && `${publicacion.capacidad} huéspedes`,
    publicacion.precioNoche && `$${publicacion.precioNoche.toLocaleString('es-CO')} por noche`,
  ];
  return partes.filter(Boolean).join(' · ');
}

export default function RevisionPublicacionesPage() {
  const [pendientes, setPendientes] = useState(null);
  const [errorCarga, setErrorCarga] = useState(null);
  const [observaciones, setObservaciones] = useState({});
  const [erroresObservaciones, setErroresObservaciones] = useState({});
  const [errores, setErrores] = useState({});
  const [enCurso, setEnCurso] = useState(null);
  const [confirmacion, setConfirmacion] = useState(null);

  useEffect(() => {
    let vigente = true;
    listarPendientesDeRevision()
      .then((lista) => vigente && setPendientes(lista))
      .catch(() => vigente && setErrorCarga(MENSAJE_ERROR));
    // Si la página se desmonta antes de la respuesta, no se toca el estado.
    return () => {
      vigente = false;
    };
  }, []);

  // La API decide si la revisión procede; si la acepta, la publicación ya no está pendiente.
  async function revisar(publicacion, ejecutar, mensaje) {
    setEnCurso(publicacion.id);
    setConfirmacion(null);
    setErrores((actuales) => ({ ...actuales, [publicacion.id]: null }));
    try {
      await ejecutar();
      setPendientes((lista) => lista.filter((otra) => otra.id !== publicacion.id));
      setConfirmacion(mensaje);
    } catch (error) {
      setErrores((actuales) => ({
        ...actuales,
        [publicacion.id]: error.estado ? error.message : MENSAJE_REVISION_FALLIDA,
      }));
    } finally {
      setEnCurso(null);
    }
  }

  function aprobar(publicacion) {
    revisar(publicacion, () => aprobarPublicacion(publicacion.id), `«${publicacion.titulo}» quedó publicada.`);
  }

  function devolver(evento, publicacion) {
    evento.preventDefault();
    const texto = observaciones[publicacion.id] ?? '';
    const error = errorDeObservaciones(texto);
    setErroresObservaciones((actuales) => ({ ...actuales, [publicacion.id]: error }));
    if (error) {
      return;
    }
    revisar(publicacion, () => devolverPublicacion(publicacion.id, texto.trim()),
      `«${publicacion.titulo}» volvió al anfitrión.`);
  }

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Revisión de publicaciones</h1>
      <p className={estilos.nota}>Aprueba las que cumplen o devuélvelas al anfitrión explicando qué corregir.</p>
      <div role="status" className={confirmacion ? estilos.confirmacion : undefined}>{confirmacion}</div>
      {errorCarga && <p role="alert" className={estilos.error}>{errorCarga}</p>}
      {!errorCarga && pendientes === null && <p className={estilos.nota}>Cargando publicaciones pendientes…</p>}
      {pendientes?.length === 0 && <p className={estilos.nota}>No hay publicaciones pendientes de revisión.</p>}
      {pendientes?.length > 0 && (
        <ul className={estilos.lista}>
          {pendientes.map((publicacion) => {
            const idObservaciones = `observaciones-${publicacion.id}`;
            const errorObservaciones = erroresObservaciones[publicacion.id];
            const ocupada = enCurso === publicacion.id;
            return (
              <li key={publicacion.id} className={estilos.tarjeta}>
                <h2 className={estilos.nombre}>{publicacion.titulo}</h2>
                <p className={estilos.detalle}>{datosDe(publicacion)}</p>
                {publicacion.enviadaARevisionEn && (
                  <p className={estilos.detalle}>
                    Enviada a revisión el{' '}
                    {new Date(publicacion.enviadaARevisionEn).toLocaleDateString('es-CO', FORMATO_FECHA)}
                  </p>
                )}
                {publicacion.descripcion && <p className={estilos.descripcion}>{publicacion.descripcion}</p>}
                {errores[publicacion.id] && (
                  <p role="alert" className={estilos.error}>{errores[publicacion.id]}</p>
                )}
                <form className={estilos.revision} onSubmit={(evento) => devolver(evento, publicacion)} noValidate>
                  <div className={estilos.campo}>
                    <label className={estilos.etiqueta} htmlFor={idObservaciones}>
                      Observaciones para {publicacion.titulo}
                    </label>
                    <textarea id={idObservaciones} className={estilos.control} rows={3} maxLength={1000}
                      value={observaciones[publicacion.id] ?? ''}
                      aria-invalid={errorObservaciones ? true : undefined}
                      aria-describedby={errorObservaciones ? `${idObservaciones}-error` : undefined}
                      onChange={(evento) => setObservaciones((actuales) => ({
                        ...actuales, [publicacion.id]: evento.target.value,
                      }))} />
                    {errorObservaciones && (
                      <p id={`${idObservaciones}-error`} className={estilos.errorCampo}>{errorObservaciones}</p>
                    )}
                  </div>
                  <div className={estilos.acciones}>
                    <button type="button" className={estilos.aprobar} disabled={ocupada}
                      aria-label={`Aprobar ${publicacion.titulo}`} onClick={() => aprobar(publicacion)}>
                      Aprobar
                    </button>
                    <button type="submit" className={estilos.devolver} disabled={ocupada}
                      aria-label={`Devolver ${publicacion.titulo}`}>
                      Devolver
                    </button>
                  </div>
                </form>
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}
