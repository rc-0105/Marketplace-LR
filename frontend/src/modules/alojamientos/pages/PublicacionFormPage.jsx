import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import Campo from '../../../shared/components/Campo.jsx';
import ConfiguracionPublicacion from '../components/ConfiguracionPublicacion.jsx';
import ImagenesPublicacion from '../components/ImagenesPublicacion.jsx';
import PasosPublicacion, { PASOS } from '../components/PasosPublicacion.jsx';
import ServiciosPublicacion from '../components/ServiciosPublicacion.jsx';
import {
  FORMULARIO_VACIO, formularioDesde, nombreDeCampo, nombreDeEstado, solicitudDesde, validarFormulario,
} from '../models/publicacion.js';
import {
  activarPublicacion, consultarPublicacion, crearPublicacion, editarPublicacion, listarTiposAlojamiento,
} from '../services/alojamientosService.js';
import estilos from './PublicacionFormPage.module.css';

const MENSAJE_CARGA = 'No pudimos cargar la publicación. Intenta de nuevo.';
const MENSAJE_PROHIBIDO = 'Esta publicación no es tuya.';
const MENSAJE_TIPOS = 'No pudimos cargar los tipos de alojamiento.';
const ULTIMO_PASO = PASOS.length;

// Paso en que se captura cada dato del formulario principal; sirve para llevar al anfitrión al paso con el error.
const PASO_DE_CAMPO = {
  titulo: 1, descripcion: 1, tipo: 1, capacidad: 1, ciudad: 2, direccion: 2, precioNoche: 3, reglas: 4,
};
// Los pasos 5 y 6 solo tienen paneles propios, que se guardan por su lado.
const PASOS_CON_FORMULARIO = [1, 2, 3, 4];

function pasoDesde(valor) {
  const numero = Number(valor);
  return Number.isInteger(numero) && numero >= 1 && numero <= ULTIMO_PASO ? numero : 1;
}

/**
 * Asistente de seis pasos para crear una publicación en borrador o, si la ruta trae un id, editar una
 * existente (RF-11, RF-13). Los datos básicos se guardan juntos; servicios, imágenes y condiciones de reserva
 * se guardan en su panel, y requieren que el borrador ya exista.
 */
export default function PublicacionFormPage() {
  const { id } = useParams();
  const editando = id !== undefined;
  const navegar = useNavigate();
  const [consulta, setConsulta] = useSearchParams();
  const paso = pasoDesde(consulta.get('paso'));
  const [tipos, setTipos] = useState([]);
  const [errorTipos, setErrorTipos] = useState(false);
  const [publicacion, setPublicacion] = useState(null);
  const [formulario, setFormulario] = useState(editando ? null : FORMULARIO_VACIO);
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [aviso, setAviso] = useState('');
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    let vigente = true;
    listarTiposAlojamiento()
      .then((lista) => vigente && setTipos(lista))
      .catch(() => vigente && setErrorTipos(true));
    if (editando) {
      consultarPublicacion(id)
        .then((cargada) => {
          if (vigente) {
            setPublicacion(cargada);
            setFormulario(formularioDesde(cargada));
          }
        })
        .catch((error) => vigente && setErrorGeneral(error.estado === 403 ? MENSAJE_PROHIBIDO : MENSAJE_CARGA));
    }
    return () => {
      vigente = false;
    };
  }, [id, editando]);

  function irA(numero) {
    setConsulta({ paso: String(numero) });
    setAviso('');
  }

  function cambiar(evento) {
    const { name, value } = evento.target;
    setFormulario((previo) => ({ ...previo, [name]: value }));
    setAviso('');
  }

  // Lleva al primer paso que tiene un dato con error, para que el anfitrión lo vea y lo corrija.
  function mostrarErrores(nuevos) {
    setErrores(nuevos);
    const pasos = Object.keys(nuevos).map((campo) => PASO_DE_CAMPO[campo]).filter(Boolean);
    if (pasos.length > 0 && !pasos.includes(paso)) {
      irA(Math.min(...pasos));
    }
  }

  /** Guarda los datos básicos. Devuelve true si quedaron guardados; al crear, sigue al paso `destino`. */
  async function guardar(destino = paso) {
    setErrorGeneral('');
    setAviso('');
    const erroresFormato = validarFormulario(formulario);
    if (Object.keys(erroresFormato).length > 0) {
      mostrarErrores(erroresFormato);
      return false;
    }
    setErrores({});

    setEnviando(true);
    try {
      if (editando) {
        const editada = await editarPublicacion(id, solicitudDesde(formulario, publicacion));
        setPublicacion(editada);
        setAviso('Guardamos los cambios.');
      } else {
        const creada = await crearPublicacion(solicitudDesde(formulario));
        navegar(`/anfitrion/publicaciones/${creada.id}/editar?paso=${destino}`, { replace: true });
      }
      return true;
    } catch (error) {
      if (Object.keys(error.errores ?? {}).length > 0) {
        mostrarErrores(error.errores);
      } else {
        setErrorGeneral(error.estado === 403 ? MENSAJE_PROHIBIDO : error.message);
      }
      return false;
    } finally {
      setEnviando(false);
    }
  }

  async function siguiente() {
    if (PASOS_CON_FORMULARIO.includes(paso)) {
      if (editando) {
        if (await guardar()) irA(paso + 1);
      } else {
        await guardar(paso + 1);
      }
    } else {
      irA(paso + 1);
    }
  }

  async function solicitarActivacion() {
    setErrorGeneral('');
    setEnviando(true);
    try {
      await activarPublicacion(id);
      navegar(`/anfitrion/publicaciones/${id}/enviada`, { replace: true });
    } catch (error) {
      const faltantes = publicacion?.camposFaltantes ?? [];
      setErrorGeneral(error.estado === 422 && faltantes.length > 0
        ? `${error.message} Completa: ${faltantes.map(nombreDeCampo).join(', ')}.`
        : error.message);
    } finally {
      setEnviando(false);
    }
  }

  function enviar(evento) {
    evento.preventDefault();
    guardar();
  }

  const titulo = editando ? 'Editar publicación' : 'Nueva publicación';
  const estado = publicacion?.estado ?? 'BORRADOR';
  const pasoActual = PASOS[paso - 1];
  const avisoSinBorrador = (
    <p className={estilos.nota}>Guarda el borrador con «Guardar borrador» o «Siguiente» para completar esta parte.</p>
  );

  return (
    <main className={estilos.pagina}>
      <Link to="/anfitrion/publicaciones" className={estilos.volver}>
        <span aria-hidden="true">‹</span> Volver a mis publicaciones
      </Link>
      <div className={estilos.encabezado}>
        <h1 className={estilos.titulo}>{titulo}</h1>
        <span className={`${estilos.estado} ${estilos[estado]}`}>{nombreDeEstado(estado)}</span>
      </div>
      {errorGeneral && <p role="alert" className={estilos.error}>{errorGeneral}</p>}
      {!formulario && !errorGeneral && <p className={estilos.nota}>Cargando la publicación…</p>}
      {formulario && (
        <div className={estilos.columnas}>
          <PasosPublicacion paso={paso} faltantes={publicacion ? publicacion.camposFaltantes : null} alIr={irA} />

          <section className={estilos.seccion} aria-labelledby="titulo-paso">
            <p className={estilos.numeroPaso}>PASO {paso} DE {ULTIMO_PASO}</p>
            <h2 id="titulo-paso" className={estilos.tituloPaso}>{pasoActual.titulo}</h2>
            {aviso && <output className={estilos.aviso}>{aviso}</output>}

            <form className={estilos.campos} onSubmit={enviar} noValidate>
              {paso === 1 && (
                <>
                  <p className={estilos.nota}>
                    Solo el título es obligatorio: puedes guardar el borrador y completarlo después.
                  </p>
                  <Campo id="titulo" etiqueta="Título" value={formulario.titulo} error={errores.titulo}
                    onChange={cambiar} maxLength={120} required />
                  <div className={estilos.campo}>
                    <label className={estilos.etiqueta} htmlFor="descripcion">Descripción</label>
                    <textarea id="descripcion" name="descripcion" className={estilos.control} rows={4}
                      maxLength={2000} value={formulario.descripcion} onChange={cambiar} />
                  </div>
                  <div className={estilos.fila}>
                    <div className={estilos.campo}>
                      <label className={estilos.etiqueta} htmlFor="tipo">Tipo de alojamiento</label>
                      <select id="tipo" name="tipo" className={estilos.control} value={formulario.tipo}
                        onChange={cambiar}>
                        <option value="">Sin definir</option>
                        {tipos.map((tipo) => <option key={tipo.id} value={String(tipo.id)}>{tipo.nombre}</option>)}
                      </select>
                      {errorTipos && <p role="alert" className={estilos.errorCampo}>{MENSAJE_TIPOS}</p>}
                      {errores.tipo && <p className={estilos.errorCampo}>{errores.tipo}</p>}
                    </div>
                    <Campo id="capacidad" etiqueta="Capacidad (huéspedes)" inputMode="numeric"
                      value={formulario.capacidad} error={errores.capacidad} onChange={cambiar} />
                  </div>
                </>
              )}

              {paso === 2 && (
                <>
                  <Campo id="ciudad" etiqueta="Ciudad" value={formulario.ciudad} error={errores.ciudad}
                    onChange={cambiar} maxLength={100} />
                  <Campo id="direccion" etiqueta="Dirección" value={formulario.direccion} error={errores.direccion}
                    onChange={cambiar} maxLength={200} />
                </>
              )}

              {paso === 3 && (
                <Campo id="precioNoche" etiqueta="Precio por noche (COP)" inputMode="decimal"
                  value={formulario.precioNoche} error={errores.precioNoche} onChange={cambiar} />
              )}

              {paso === 4 && (
                <div className={estilos.campo}>
                  <label className={estilos.etiqueta} htmlFor="reglas">Reglas de la casa</label>
                  <textarea id="reglas" name="reglas" className={estilos.control} rows={3}
                    maxLength={2000} value={formulario.reglas} onChange={cambiar} />
                </div>
              )}
            </form>

            {/* Cada panel guarda por su lado; la publicación que devuelve mantiene al día lo que envía el PUT. */}
            {paso === 3 && (publicacion
              ? <ConfiguracionPublicacion parte="precio" publicacionId={id} publicacion={publicacion}
                alGuardar={setPublicacion} />
              : avisoSinBorrador)}
            {paso === 4 && (publicacion
              ? <ServiciosPublicacion publicacionId={id} seleccionados={publicacion.servicios}
                alGuardar={setPublicacion} />
              : avisoSinBorrador)}
            {paso === 5 && (publicacion
              ? <ImagenesPublicacion publicacionId={id} imagenes={publicacion.imagenes}
                alGuardar={setPublicacion} />
              : avisoSinBorrador)}
            {paso === 6 && (publicacion
              ? <ConfiguracionPublicacion parte="reserva" publicacionId={id} publicacion={publicacion}
                alGuardar={setPublicacion} />
              : avisoSinBorrador)}

            <div className={estilos.acciones}>
              <div>
                {paso > 1 && (
                  <button type="button" className={estilos.secundario} onClick={() => irA(paso - 1)}>Atrás</button>
                )}
              </div>
              <div className={estilos.derecha}>
                <button type="button" className={estilos.secundario} disabled={enviando} onClick={() => guardar()}>
                  {editando ? 'Guardar cambios' : 'Guardar borrador'}
                </button>
                {paso < ULTIMO_PASO && (
                  <button type="button" className={estilos.primario} disabled={enviando} onClick={siguiente}>
                    Siguiente
                  </button>
                )}
                {paso === ULTIMO_PASO && publicacion && estado === 'BORRADOR' && (
                  <button type="button" className={estilos.primario} disabled={enviando}
                    onClick={solicitarActivacion}>
                    Solicitar activación
                  </button>
                )}
                {paso === ULTIMO_PASO && publicacion && estado !== 'BORRADOR' && (
                  <button type="button" className={estilos.primario}
                    onClick={() => navegar('/anfitrion/publicaciones')}>
                    Terminar
                  </button>
                )}
              </div>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}
