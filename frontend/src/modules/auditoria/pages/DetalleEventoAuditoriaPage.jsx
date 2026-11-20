import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { formatearDatos, formatearFechaBogota } from '../models/eventoAuditoria.js';
import { detalle } from '../services/auditoriaService.js';
import estilos from './DetalleEventoAuditoriaPage.module.css';

const MENSAJE_NO_ENCONTRADO = 'No encontramos ese evento de auditoría.';
const MENSAJE_ERROR = 'No pudimos cargar el evento. Intenta de nuevo.';
const NOMBRE_RESULTADO = { EXITO: 'Éxito', FALLO: 'Fallo' };

/** Detalle de solo lectura de un evento de auditoría (RF-59). La búsqueda de la lista viaja en la dirección. */
export default function DetalleEventoAuditoriaPage() {
  const { id } = useParams();
  const { search } = useLocation();
  const [evento, setEvento] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    // Si cambia el id o se desmonta la página, la respuesta de la carga anterior ya no aplica.
    let vigente = true;
    setEvento(null);
    setError(null);
    detalle(id)
      .then((datos) => vigente && setEvento(datos))
      .catch((fallo) => vigente && setError(fallo.estado === 404 ? MENSAJE_NO_ENCONTRADO : MENSAJE_ERROR));
    return () => {
      vigente = false;
    };
  }, [id]);

  return (
    <main className={estilos.pagina}>
      <Link to={`/administrador/auditoria${search}`} className={estilos.volver}>Volver a la lista</Link>
      {error && <p role="alert" className={estilos.error}>{error}</p>}
      {!error && evento === null && <output className={estilos.nota}>Cargando evento…</output>}
      {evento && (
        <>
          <h1 className={estilos.titulo}>Evento de auditoría {evento.id}</h1>
          <dl className={estilos.campos}>
            <dt>Fecha (Bogotá)</dt>
            <dd>{formatearFechaBogota(evento.fecha)}</dd>
            <dt>Usuario</dt>
            <dd>{evento.usuario ?? 'Sin usuario'}</dd>
            <dt>Evento</dt>
            <dd>{evento.accion}</dd>
            <dt>Entidad</dt>
            <dd>{evento.entidad}</dd>
            <dt>Id de la entidad</dt>
            <dd>{evento.entidadId ?? 'Sin id'}</dd>
            <dt>Resultado</dt>
            <dd>{NOMBRE_RESULTADO[evento.resultado] ?? evento.resultado}</dd>
            <dt>Correlación</dt>
            <dd>{evento.correlacion ?? 'Sin correlación'}</dd>
          </dl>
          <h2 className={estilos.subtitulo}>Datos</h2>
          <pre className={estilos.datos} aria-label="Datos del evento">{formatearDatos(evento.datos)}</pre>
        </>
      )}
    </main>
  );
}
