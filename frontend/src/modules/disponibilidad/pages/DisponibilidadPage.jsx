import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import FormularioPeriodo from '../components/FormularioPeriodo.jsx';
import { fechaLegible } from '../models/periodo.js';
import {
  eliminarPeriodo,
  listarPeriodos,
  modificarPeriodo,
  registrarPeriodo,
} from '../services/disponibilidadService.js';
import estilos from './DisponibilidadPage.module.css';

const MENSAJE_CARGA = 'No pudimos cargar la disponibilidad. Intenta de nuevo.';
const MENSAJE_ELIMINAR = 'No pudimos eliminar el periodo. Intenta de nuevo.';

// Las fechas ISO se ordenan bien como texto: así la lista sigue del más próximo al más lejano.
function ordenados(periodos) {
  return [...periodos].sort((a, b) => a.fechaInicio.localeCompare(b.fechaInicio));
}

/** Periodos disponibles de una publicación del anfitrión: los registra, modifica y elimina (RF-23, RF-24). */
export default function DisponibilidadPage() {
  const { id } = useParams();
  const [periodos, setPeriodos] = useState(null);
  const [errorCarga, setErrorCarga] = useState(null);
  const [editando, setEditando] = useState(null);
  const [errores, setErrores] = useState({});
  const [eliminando, setEliminando] = useState(null);

  useEffect(() => {
    let vigente = true;
    listarPeriodos(id)
      .then((lista) => vigente && setPeriodos(lista))
      .catch((error) => vigente && setErrorCarga(error.estado ? error.message : MENSAJE_CARGA));
    // Si la página se desmonta antes de la respuesta, no se toca el estado.
    return () => {
      vigente = false;
    };
  }, [id]);

  async function registrar(fechas) {
    const creado = await registrarPeriodo(id, fechas);
    setPeriodos((lista) => ordenados([...lista, creado]));
  }

  async function modificar(periodo, fechas) {
    const modificado = await modificarPeriodo(periodo.id, fechas);
    setPeriodos((lista) => ordenados(lista.map((otro) => (otro.id === modificado.id ? modificado : otro))));
    setEditando(null);
  }

  async function eliminar(periodo) {
    setEliminando(periodo.id);
    setErrores((actuales) => ({ ...actuales, [periodo.id]: null }));
    try {
      await eliminarPeriodo(periodo.id);
      setPeriodos((lista) => lista.filter((otro) => otro.id !== periodo.id));
    } catch (error) {
      setErrores((actuales) => ({ ...actuales, [periodo.id]: error.estado ? error.message : MENSAJE_ELIMINAR }));
    } finally {
      setEliminando(null);
    }
  }

  return (
    <main className={estilos.pagina}>
      <Link to="/anfitrion/publicaciones" className={estilos.volver}>Volver a mis publicaciones</Link>
      <h1 className={estilos.titulo}>Disponibilidad</h1>
      <p className={estilos.nota}>
        Registra las fechas en que puedes recibir huéspedes. Solo se reserva dentro de estos periodos.
      </p>
      {errorCarga && <p role="alert" className={estilos.error}>{errorCarga}</p>}
      {!errorCarga && periodos === null && <p className={estilos.nota}>Cargando la disponibilidad…</p>}
      {periodos !== null && (
        <>
          <section className={estilos.seccion} aria-labelledby="titulo-agregar">
            <h2 id="titulo-agregar" className={estilos.subtitulo}>Nuevo periodo</h2>
            <FormularioPeriodo id="nuevo" titulo="Nuevo periodo" etiquetaEnviar="Agregar periodo"
              alGuardar={registrar} />
          </section>
          <section className={estilos.seccion} aria-labelledby="titulo-periodos">
            <h2 id="titulo-periodos" className={estilos.subtitulo}>Periodos registrados</h2>
            {periodos.length === 0 && (
              <p className={estilos.nota}>Todavía no hay periodos. Agrega el primero para empezar a recibir reservas.</p>
            )}
            {periodos.length > 0 && (
              <ul className={estilos.lista}>
                {periodos.map((periodo) => {
                  const rango = `${fechaLegible(periodo.fechaInicio)} al ${fechaLegible(periodo.fechaFin)}`;
                  return (
                    <li key={periodo.id} className={estilos.tarjeta}>
                      {editando === periodo.id ? (
                        <FormularioPeriodo id={`periodo-${periodo.id}`} titulo={`Editar periodo del ${rango}`}
                          etiquetaEnviar="Guardar cambios"
                          inicial={{ fechaInicio: periodo.fechaInicio, fechaFin: periodo.fechaFin }}
                          alGuardar={(fechas) => modificar(periodo, fechas)}
                          alCancelar={() => setEditando(null)} />
                      ) : (
                        <>
                          <p className={estilos.rango}>{rango}</p>
                          {errores[periodo.id] && (
                            <p role="alert" className={estilos.error}>{errores[periodo.id]}</p>
                          )}
                          <div className={estilos.acciones}>
                            <button type="button" className={estilos.accion}
                              aria-label={`Editar periodo del ${rango}`}
                              onClick={() => setEditando(periodo.id)}>
                              Editar
                            </button>
                            <button type="button" className={estilos.accion}
                              aria-label={`Eliminar periodo del ${rango}`}
                              disabled={eliminando === periodo.id}
                              onClick={() => eliminar(periodo)}>
                              Eliminar
                            </button>
                          </div>
                        </>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        </>
      )}
    </main>
  );
}
