import { useRef, useState } from 'react';
import PropTypes from 'prop-types';
import Campo from '../../../shared/components/Campo.jsx';
import { MAXIMO_IMAGENES, errorDeArchivo, errorDeImagen } from '../models/publicacion.js';
import { actualizarImagenes, subirImagen } from '../services/alojamientosService.js';
import estilos from './ImagenesPublicacion.module.css';

/**
 * Referencias de imagen de la publicación, en orden: la primera es la principal.
 * Se agregan arrastrando archivos PNG o JPG (se suben a la plataforma) o pegando una URL. Los cambios
 * quedan en la lista hasta que se guardan (RF-19).
 */
export default function ImagenesPublicacion({ publicacionId, imagenes, alGuardar }) {
  const [guardadas, setGuardadas] = useState(imagenes);
  const [lista, setLista] = useState(imagenes);
  const sinGuardar = lista.length !== guardadas.length || lista.some((url, indice) => url !== guardadas[indice]);
  const [nueva, setNueva] = useState('');
  const [errorNueva, setErrorNueva] = useState(undefined);
  const [enviando, setEnviando] = useState(false);
  const [mensaje, setMensaje] = useState(null);
  const [subiendo, setSubiendo] = useState(false);
  const [arrastrando, setArrastrando] = useState(false);
  const [erroresArchivos, setErroresArchivos] = useState([]);
  const entrada = useRef(null);

  function cambiarLista(siguiente) {
    setLista(siguiente);
    setMensaje(null);
  }

  // Revisa cada archivo, sube los válidos de uno en uno y deja sus URL en la lista; los que fallan se explican.
  async function recibirArchivos(archivos) {
    setMensaje(null);
    const errores = [];
    const validos = [];
    const cupo = MAXIMO_IMAGENES - lista.length;
    for (const archivo of archivos) {
      const error = errorDeArchivo(archivo);
      if (error) {
        errores.push(error);
      } else if (validos.length >= cupo) {
        errores.push(`«${archivo.name}» no cabe: se admiten máximo ${MAXIMO_IMAGENES} imágenes.`);
      } else {
        validos.push(archivo);
      }
    }
    setErroresArchivos(errores);
    if (validos.length === 0) {
      return;
    }
    setSubiendo(true);
    for (const archivo of validos) {
      try {
        const url = await subirImagen(publicacionId, archivo);
        setLista((previa) => [...previa, url]);
      } catch (error) {
        errores.push(`«${archivo.name}»: ${error.message}`);
        setErroresArchivos([...errores]);
      }
    }
    setSubiendo(false);
  }

  function alSoltar(evento) {
    evento.preventDefault();
    setArrastrando(false);
    recibirArchivos(Array.from(evento.dataTransfer.files));
  }

  function alElegir(evento) {
    recibirArchivos(Array.from(evento.target.files));
    // Sin esto, elegir de nuevo el mismo archivo no dispara el evento.
    evento.target.value = '';
  }

  function agregar(evento) {
    evento.preventDefault();
    const error = errorDeImagen(nueva, lista);
    setErrorNueva(error);
    if (!error) {
      cambiarLista([...lista, nueva.trim()]);
      setNueva('');
    }
  }

  function mover(indice, desplazamiento) {
    const siguiente = [...lista];
    const [imagen] = siguiente.splice(indice, 1);
    siguiente.splice(indice + desplazamiento, 0, imagen);
    cambiarLista(siguiente);
  }

  async function guardar() {
    setEnviando(true);
    setMensaje(null);
    try {
      const actualizada = await actualizarImagenes(publicacionId, lista);
      setLista(actualizada.imagenes);
      setGuardadas(actualizada.imagenes);
      alGuardar(actualizada);
      setMensaje({ error: false, texto: 'Guardamos las imágenes.' });
    } catch (error) {
      setMensaje({ error: true, texto: error.message });
    } finally {
      setEnviando(false);
    }
  }

  return (
    <section className={estilos.seccion} aria-labelledby="titulo-imagenes">
      <h2 id="titulo-imagenes" className={estilos.titulo}>Imágenes</h2>
      <p className={estilos.nota}>La primera imagen es la principal. Usa las flechas para cambiar el orden.</p>

      <div className={`${estilos.zona} ${arrastrando ? estilos.zonaActiva : ''}`}
        onDragOver={(evento) => {
          evento.preventDefault();
          setArrastrando(true);
        }}
        onDragLeave={() => setArrastrando(false)}
        onDrop={alSoltar}>
        <p className={estilos.zonaTitulo}>Arrastra tus fotos aquí</p>
        <p className={estilos.nota}>PNG o JPG, de hasta 5 MB cada una.</p>
        <button type="button" className={estilos.botonSecundario} disabled={subiendo}
          onClick={() => entrada.current.click()}>
          Elegir archivos
        </button>
        <input ref={entrada} type="file" accept="image/png,image/jpeg" multiple hidden
          aria-label="Archivos de imagen" onChange={alElegir} />
        {subiendo && <p role="status" className={estilos.nota}>Subiendo imágenes…</p>}
      </div>
      {erroresArchivos.length > 0 && (
        <ul role="alert" className={estilos.erroresArchivos}>
          {erroresArchivos.map((error) => <li key={error}>{error}</li>)}
        </ul>
      )}

      {lista.length === 0 && <p className={estilos.nota}>Todavía no hay imágenes.</p>}
      {lista.length > 0 && (
        <ol className={estilos.lista}>
          {lista.map((url, indice) => (
            <li key={`${indice}-${url}`} className={estilos.imagen}>
              <img src={url} alt={`Imagen ${indice + 1}`} className={estilos.miniatura} />
              <span className={estilos.url}>
                {indice === 0 ? <strong className={estilos.principal}>Principal</strong> : 'Secundaria'}
              </span>
              <span className={estilos.acciones}>
                <button type="button" onClick={() => mover(indice, -1)} disabled={indice === 0}
                  aria-label={`Subir la imagen ${indice + 1}`}>↑</button>
                <button type="button" onClick={() => mover(indice, 1)} disabled={indice === lista.length - 1}
                  aria-label={`Bajar la imagen ${indice + 1}`}>↓</button>
                <button type="button" onClick={() => cambiarLista(lista.filter((_, otro) => otro !== indice))}
                  aria-label={`Quitar la imagen ${indice + 1}`}>Quitar</button>
              </span>
            </li>
          ))}
        </ol>
      )}
      <form className={estilos.agregar} onSubmit={agregar} noValidate>
        <Campo id="nueva-imagen" etiqueta="URL de la imagen" type="url" value={nueva} error={errorNueva}
          onChange={(evento) => setNueva(evento.target.value)} />
        <button type="submit" className={estilos.botonSecundario}>Agregar imagen</button>
      </form>
      {sinGuardar && <p className={estilos.nota}>Hay cambios en las imágenes sin guardar.</p>}
      {mensaje && (
        <p role={mensaje.error ? 'alert' : 'status'} className={mensaje.error ? estilos.error : estilos.aviso}>
          {mensaje.texto}
        </p>
      )}
      <button type="button" className={estilos.boton} onClick={guardar} disabled={enviando}>
        Guardar imágenes
      </button>
    </section>
  );
}

ImagenesPublicacion.propTypes = {
  publicacionId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
  imagenes: PropTypes.arrayOf(PropTypes.string).isRequired,
  alGuardar: PropTypes.func.isRequired,
};
