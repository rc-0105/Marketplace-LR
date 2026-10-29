import { useState } from 'react';
import PropTypes from 'prop-types';
import Campo from '../../../shared/components/Campo.jsx';
import { errorDeImagen } from '../models/publicacion.js';
import { actualizarImagenes } from '../services/alojamientosService.js';
import estilos from './ImagenesPublicacion.module.css';

/**
 * Referencias de imagen de la publicación, en orden: la primera es la principal.
 * Los cambios quedan en la lista hasta que se guardan (RF-19).
 */
export default function ImagenesPublicacion({ publicacionId, imagenes, alGuardar }) {
  const [lista, setLista] = useState(imagenes);
  const [nueva, setNueva] = useState('');
  const [errorNueva, setErrorNueva] = useState(undefined);
  const [enviando, setEnviando] = useState(false);
  const [mensaje, setMensaje] = useState(null);

  function cambiarLista(siguiente) {
    setLista(siguiente);
    setMensaje(null);
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
      {lista.length === 0 && <p className={estilos.nota}>Todavía no hay imágenes.</p>}
      {lista.length > 0 && (
        <ol className={estilos.lista}>
          {lista.map((url, indice) => (
            <li key={`${indice}-${url}`} className={estilos.imagen}>
              <img src={url} alt={`Imagen ${indice + 1}`} className={estilos.miniatura} />
              <span className={estilos.url}>
                {indice === 0 && <strong className={estilos.principal}>Principal · </strong>}
                {url}
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
