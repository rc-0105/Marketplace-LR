import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import Campo from '../../../shared/components/Campo.jsx';
import ImagenesPublicacion from '../components/ImagenesPublicacion.jsx';
import ServiciosPublicacion from '../components/ServiciosPublicacion.jsx';
import { FORMULARIO_VACIO, formularioDesde, solicitudDesde, validarFormulario } from '../models/publicacion.js';
import {
  consultarPublicacion, crearPublicacion, editarPublicacion, listarTiposAlojamiento,
} from '../services/alojamientosService.js';
import estilos from './PublicacionFormPage.module.css';

const MENSAJE_CARGA = 'No pudimos cargar la publicación. Intenta de nuevo.';
const MENSAJE_PROHIBIDO = 'Esta publicación no es tuya.';

/** Crea una publicación en borrador o, si la ruta trae un id, edita una existente (RF-11, RF-13). */
export default function PublicacionFormPage() {
  const { id } = useParams();
  const editando = id !== undefined;
  const navegar = useNavigate();
  const [tipos, setTipos] = useState([]);
  const [publicacion, setPublicacion] = useState(null);
  const [formulario, setFormulario] = useState(editando ? null : FORMULARIO_VACIO);
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [aviso, setAviso] = useState('');
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    let vigente = true;
    listarTiposAlojamiento().then((lista) => vigente && setTipos(lista)).catch(() => {});
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

  function cambiar(evento) {
    const { name, value } = evento.target;
    setFormulario((previo) => ({ ...previo, [name]: value }));
    setAviso('');
  }

  async function enviar(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    setAviso('');
    const erroresFormato = validarFormulario(formulario);
    setErrores(erroresFormato);
    if (Object.keys(erroresFormato).length > 0) {
      return;
    }

    setEnviando(true);
    try {
      if (editando) {
        const editada = await editarPublicacion(id, solicitudDesde(formulario, publicacion));
        setPublicacion(editada);
        setAviso('Guardamos los cambios.');
      } else {
        const creada = await crearPublicacion(solicitudDesde(formulario));
        navegar(`/anfitrion/publicaciones/${creada.id}/editar`, { replace: true });
      }
    } catch (error) {
      if (Object.keys(error.errores ?? {}).length > 0) {
        setErrores(error.errores);
      } else {
        setErrorGeneral(error.estado === 403 ? MENSAJE_PROHIBIDO : error.message);
      }
    } finally {
      setEnviando(false);
    }
  }

  const titulo = editando ? 'Editar publicación' : 'Nueva publicación';

  return (
    <main className={estilos.pagina}>
      <Link to="/anfitrion/publicaciones" className={estilos.volver}>Volver a mis publicaciones</Link>
      <h1 className={estilos.titulo}>{titulo}</h1>
      {errorGeneral && <p role="alert" className={estilos.error}>{errorGeneral}</p>}
      {aviso && <p role="status" className={estilos.aviso}>{aviso}</p>}
      {!formulario && !errorGeneral && <p className={estilos.nota}>Cargando la publicación…</p>}
      {formulario && (
        <form className={estilos.formulario} onSubmit={enviar} noValidate>
          <p className={estilos.nota}>Solo el título es obligatorio: puedes guardar el borrador y completarlo después.</p>
          <Campo id="titulo" etiqueta="Título" value={formulario.titulo} error={errores.titulo}
            onChange={cambiar} maxLength={120} required />
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="descripcion">Descripción</label>
            <textarea id="descripcion" name="descripcion" className={estilos.control} rows={4}
              maxLength={2000} value={formulario.descripcion} onChange={cambiar} />
          </div>
          <Campo id="ciudad" etiqueta="Ciudad" value={formulario.ciudad} error={errores.ciudad}
            onChange={cambiar} maxLength={100} />
          <Campo id="direccion" etiqueta="Dirección" value={formulario.direccion} error={errores.direccion}
            onChange={cambiar} maxLength={200} />
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="tipo">Tipo de alojamiento</label>
            <select id="tipo" name="tipo" className={estilos.control} value={formulario.tipo} onChange={cambiar}>
              <option value="">Sin definir</option>
              {tipos.map((tipo) => <option key={tipo.id} value={String(tipo.id)}>{tipo.nombre}</option>)}
            </select>
            {errores.tipo && <p className={estilos.errorCampo}>{errores.tipo}</p>}
          </div>
          <div className={estilos.fila}>
            <Campo id="capacidad" etiqueta="Capacidad (huéspedes)" inputMode="numeric" value={formulario.capacidad}
              error={errores.capacidad} onChange={cambiar} />
            <Campo id="precioNoche" etiqueta="Precio por noche (COP)" inputMode="decimal"
              value={formulario.precioNoche} error={errores.precioNoche} onChange={cambiar} />
          </div>
          <div className={estilos.campo}>
            <label className={estilos.etiqueta} htmlFor="reglas">Reglas de la casa</label>
            <textarea id="reglas" name="reglas" className={estilos.control} rows={3}
              maxLength={2000} value={formulario.reglas} onChange={cambiar} />
          </div>
          <button type="submit" className={estilos.boton} disabled={enviando}>
            {editando ? 'Guardar cambios' : 'Guardar borrador'}
          </button>
        </form>
      )}
      {/* Cada panel guarda por su lado; la publicación que devuelve mantiene al día lo que envía el PUT. */}
      {publicacion && (
        <>
          <ServiciosPublicacion publicacionId={id} seleccionados={publicacion.servicios} alGuardar={setPublicacion} />
          <ImagenesPublicacion publicacionId={id} imagenes={publicacion.imagenes} alGuardar={setPublicacion} />
        </>
      )}
    </main>
  );
}
