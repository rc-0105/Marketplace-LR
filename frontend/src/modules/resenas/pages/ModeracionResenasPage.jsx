import { useEffect, useState } from 'react';
import Icono from '../../../shared/components/Icono.jsx';
import { errorDeMotivo, MOTIVO_MAXIMO } from '../models/resena.js';
import { listarResenasParaModerar, ocultarResena } from '../services/resenaService.js';
import estilos from './ModeracionResenasPage.module.css';

const MENSAJE_ERROR = 'No pudimos cargar las reseñas. Intenta de nuevo.';
const MENSAJE_FALLO = 'No pudimos ocultar la reseña. Intenta de nuevo.';

// Las fechas del negocio se muestran en hora de Colombia, sin importar dónde esté el navegador.
const FORMATO_FECHA = { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'America/Bogota' };

/**
 * Moderación de reseñas del administrador (SOF-82, HU-37): lista las reseñas visibles y oculta las que incumplen
 * las políticas, siempre con un motivo. Una reseña oculta sale de la lista; la API decide si la acción procede.
 */
export default function ModeracionResenasPage() {
  const [resenas, setResenas] = useState(null);
  const [errorCarga, setErrorCarga] = useState(null);
  const [motivos, setMotivos] = useState({});
  const [erroresMotivo, setErroresMotivo] = useState({});
  const [errores, setErrores] = useState({});
  const [enCurso, setEnCurso] = useState(null);
  const [confirmacion, setConfirmacion] = useState(null);

  useEffect(() => {
    let vigente = true;
    listarResenasParaModerar()
      .then((lista) => vigente && setResenas(lista))
      .catch(() => vigente && setErrorCarga(MENSAJE_ERROR));
    // Si la página se desmonta antes de la respuesta, no se toca el estado.
    return () => {
      vigente = false;
    };
  }, []);

  async function ocultar(evento, resena) {
    evento.preventDefault();
    const texto = motivos[resena.id] ?? '';
    const error = errorDeMotivo(texto);
    setErroresMotivo((actuales) => ({ ...actuales, [resena.id]: error }));
    if (error) {
      return;
    }
    setEnCurso(resena.id);
    setConfirmacion(null);
    setErrores((actuales) => ({ ...actuales, [resena.id]: null }));
    try {
      await ocultarResena(resena.id, texto.trim());
      setResenas((lista) => lista.filter((otra) => otra.id !== resena.id));
      setConfirmacion(`La reseña de ${resena.autor} quedó oculta.`);
    } catch (falla) {
      // Solo los errores de la API traen un mensaje pensado para el usuario; sin conexión, el aviso genérico.
      setErrores((actuales) => ({ ...actuales, [resena.id]: falla.estado ? falla.message : MENSAJE_FALLO }));
    } finally {
      setEnCurso(null);
    }
  }

  return (
    <main className={estilos.pagina}>
      <header className={estilos.cabecera}>
        <div>
          <h1 className={estilos.titulo}>Moderación de reseñas</h1>
          <p className={estilos.nota}>Oculta las reseñas que incumplen las políticas indicando el motivo.</p>
        </div>
        {resenas?.length > 0 && <span className={estilos.contador}>{resenas.length} visibles</span>}
      </header>
      <output className={confirmacion ? estilos.confirmacion : undefined}>{confirmacion}</output>
      {errorCarga && <p role="alert" className={estilos.error}>{errorCarga}</p>}
      {!errorCarga && resenas === null && <p className={estilos.nota}>Cargando reseñas…</p>}
      {resenas?.length === 0 && (
        <div className={estilos.vacio}>
          <span className={estilos.vacioIcono}><Icono nombre="resenas" /></span>
          <p className={estilos.vacioTexto}>No hay reseñas visibles para moderar.</p>
          <p className={estilos.nota}>Las reseñas nuevas de los huéspedes aparecerán aquí.</p>
        </div>
      )}
      {resenas?.length > 0 && (
        <ul className={estilos.lista}>
          {resenas.map((resena) => {
            const idMotivo = `motivo-${resena.id}`;
            const errorMotivo = erroresMotivo[resena.id];
            return (
              <li key={resena.id} className={estilos.tarjeta}>
                <div className={estilos.resena}>
                  <div className={estilos.autor}>
                    <span className={estilos.avatar} aria-hidden="true">
                      {resena.autor.trim().charAt(0).toUpperCase()}
                    </span>
                    <div className={estilos.datosAutor}>
                      <h2 className={estilos.nombre}>{resena.autor}</h2>
                      <p className={estilos.detalle}>
                        {resena.calificacion} de 5 · Alojamiento {resena.publicacionId} ·{' '}
                        {new Date(resena.fecha).toLocaleDateString('es-CO', FORMATO_FECHA)}
                      </p>
                    </div>
                  </div>
                  <div className={estilos.estrellas} aria-hidden="true">
                    {[1, 2, 3, 4, 5].map((numero) => (
                      <Icono key={numero} nombre="resenas"
                        className={numero <= resena.calificacion ? estilos.estrellaLlena : estilos.estrella} />
                    ))}
                  </div>
                  <p className={estilos.contenido}>{resena.contenido}</p>
                </div>
                {errores[resena.id] && <p role="alert" className={estilos.error}>{errores[resena.id]}</p>}
                <form className={estilos.moderacion} onSubmit={(evento) => ocultar(evento, resena)} noValidate>
                  <div className={estilos.campo}>
                    <label className={estilos.etiqueta} htmlFor={idMotivo}>
                      Motivo para ocultar la reseña de {resena.autor}
                    </label>
                    <textarea id={idMotivo} className={estilos.control} rows={3} maxLength={MOTIVO_MAXIMO}
                      placeholder="Explica qué política incumple."
                      value={motivos[resena.id] ?? ''}
                      aria-invalid={errorMotivo ? true : undefined}
                      aria-describedby={errorMotivo ? `${idMotivo}-error` : undefined}
                      onChange={(evento) => setMotivos((actuales) => ({
                        ...actuales, [resena.id]: evento.target.value,
                      }))} />
                    {errorMotivo && <p id={`${idMotivo}-error`} className={estilos.errorCampo}>{errorMotivo}</p>}
                  </div>
                  <button type="submit" className={estilos.ocultar} disabled={enCurso === resena.id}
                    aria-label={`Ocultar la reseña de ${resena.autor}`}>
                    <Icono nombre="ocultar" />
                    {enCurso === resena.id ? 'Ocultando…' : 'Ocultar'}
                  </button>
                </form>
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}
