import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { actualizarServicios, listarServicios } from '../services/alojamientosService.js';
import estilos from './ServiciosPublicacion.module.css';

/** Servicios de la publicación elegidos del catálogo; se guardan sin tocar el resto (RF-18). */
export default function ServiciosPublicacion({ publicacionId, seleccionados, alGuardar }) {
  const [catalogo, setCatalogo] = useState(null);
  const [guardados, setGuardados] = useState(() => new Set(seleccionados.map((servicio) => servicio.id)));
  const [marcados, setMarcados] = useState(guardados);
  const sinGuardar = marcados.size !== guardados.size || [...marcados].some((id) => !guardados.has(id));
  const [enviando, setEnviando] = useState(false);
  const [mensaje, setMensaje] = useState(null);

  useEffect(() => {
    let vigente = true;
    listarServicios()
      .then((lista) => vigente && setCatalogo(lista))
      .catch(() => vigente && setMensaje({ error: true, texto: 'No pudimos cargar el catálogo de servicios.' }));
    return () => {
      vigente = false;
    };
  }, []);

  function alternar(id) {
    setMensaje(null);
    setMarcados((previos) => {
      const siguientes = new Set(previos);
      if (siguientes.has(id)) {
        siguientes.delete(id);
      } else {
        siguientes.add(id);
      }
      return siguientes;
    });
  }

  async function guardar() {
    setEnviando(true);
    setMensaje(null);
    try {
      const actualizada = await actualizarServicios(publicacionId, [...marcados]);
      setGuardados(new Set(actualizada.servicios.map((servicio) => servicio.id)));
      alGuardar(actualizada);
      setMensaje({ error: false, texto: 'Guardamos los servicios.' });
    } catch (error) {
      setMensaje({ error: true, texto: error.message });
    } finally {
      setEnviando(false);
    }
  }

  return (
    <section className={estilos.seccion} aria-labelledby="titulo-servicios">
      <h2 id="titulo-servicios" className={estilos.titulo}>Servicios</h2>
      {catalogo === null && !mensaje && <p className={estilos.nota}>Cargando el catálogo…</p>}
      {catalogo && (
        <fieldset className={estilos.opciones}>
          <legend className={estilos.leyenda}>¿Qué ofrece el alojamiento?</legend>
          {catalogo.map((servicio) => (
            <label key={servicio.id} className={estilos.opcion}>
              <input type="checkbox" checked={marcados.has(servicio.id)} onChange={() => alternar(servicio.id)} />
              {servicio.nombre}
            </label>
          ))}
        </fieldset>
      )}
      {sinGuardar && <p className={estilos.nota}>Hay cambios en los servicios sin guardar.</p>}
      {mensaje && (
        <p role={mensaje.error ? 'alert' : 'status'} className={mensaje.error ? estilos.error : estilos.aviso}>
          {mensaje.texto}
        </p>
      )}
      <button type="button" className={estilos.boton} onClick={guardar} disabled={enviando || !catalogo}>
        Guardar servicios
      </button>
    </section>
  );
}

ServiciosPublicacion.propTypes = {
  publicacionId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
  seleccionados: PropTypes.arrayOf(PropTypes.shape({ id: PropTypes.number, nombre: PropTypes.string })).isRequired,
  alGuardar: PropTypes.func.isRequired,
};
