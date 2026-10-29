import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import Campo from '../../../shared/components/Campo.jsx';
import {
  configuracionDesde, errorDeTarifa, erroresDeServicioAdicional, solicitudConfiguracionDesde,
} from '../models/publicacion.js';
import { actualizarConfiguracion, listarPoliticasCancelacion } from '../services/alojamientosService.js';
import estilos from './ConfiguracionPublicacion.module.css';

const MODOS = [
  { valor: 'INMEDIATA', etiqueta: 'Reserva inmediata' },
  { valor: 'APROBACION', etiqueta: 'Requiere mi aprobación' },
];

/**
 * Condiciones de reserva de la publicación: tarifa de limpieza, modo de reserva,
 * política de cancelación y servicios adicionales (RF-20, RF-21, RF-22). Se
 * guardan por separado, sin tocar el resto.
 */
export default function ConfiguracionPublicacion({ publicacionId, publicacion, alGuardar }) {
  const [politicas, setPoliticas] = useState([]);
  const [errorPoliticas, setErrorPoliticas] = useState(false);
  const [guardada, setGuardada] = useState(() => configuracionDesde(publicacion));
  const [configuracion, setConfiguracion] = useState(guardada);
  const [errorTarifa, setErrorTarifa] = useState(undefined);
  const [nuevo, setNuevo] = useState({ nombre: '', costo: '' });
  const [erroresNuevo, setErroresNuevo] = useState({});
  const [enviando, setEnviando] = useState(false);
  const [mensaje, setMensaje] = useState(null);
  const sinGuardar = JSON.stringify(solicitudConfiguracionDesde(configuracion))
    !== JSON.stringify(solicitudConfiguracionDesde(guardada));
  const politicaElegida = politicas.find((politica) => String(politica.id) === configuracion.politica);

  useEffect(() => {
    let vigente = true;
    listarPoliticasCancelacion()
      .then((lista) => vigente && setPoliticas(lista))
      .catch(() => vigente && setErrorPoliticas(true));
    return () => {
      vigente = false;
    };
  }, []);

  function cambiar(campo, valor) {
    setConfiguracion((previa) => ({ ...previa, [campo]: valor }));
    setMensaje(null);
  }

  function agregar(evento) {
    evento.preventDefault();
    const errores = erroresDeServicioAdicional(nuevo.nombre, nuevo.costo);
    setErroresNuevo(errores);
    if (Object.keys(errores).length === 0) {
      cambiar('serviciosAdicionales', [
        ...configuracion.serviciosAdicionales,
        { nombre: nuevo.nombre.trim(), costo: Number(nuevo.costo.trim()) },
      ]);
      setNuevo({ nombre: '', costo: '' });
    }
  }

  function quitar(indice) {
    cambiar('serviciosAdicionales', configuracion.serviciosAdicionales.filter((_, otro) => otro !== indice));
  }

  async function guardar() {
    const error = errorDeTarifa(configuracion.tarifaLimpieza);
    setErrorTarifa(error);
    setMensaje(null);
    if (error) {
      return;
    }
    setEnviando(true);
    try {
      const actualizada = await actualizarConfiguracion(publicacionId, solicitudConfiguracionDesde(configuracion));
      const confirmada = configuracionDesde(actualizada);
      setGuardada(confirmada);
      setConfiguracion(confirmada);
      alGuardar(actualizada);
      setMensaje({ error: false, texto: 'Guardamos las condiciones.' });
    } catch (errorApi) {
      setMensaje({ error: true, texto: errorApi.message });
    } finally {
      setEnviando(false);
    }
  }

  return (
    <section className={estilos.seccion} aria-labelledby="titulo-condiciones">
      <h2 id="titulo-condiciones" className={estilos.titulo}>Condiciones de reserva</h2>

      <Campo id="tarifa-limpieza" etiqueta="Tarifa de limpieza (COP)" inputMode="decimal"
        value={configuracion.tarifaLimpieza} error={errorTarifa}
        onChange={(evento) => cambiar('tarifaLimpieza', evento.target.value)} />

      <fieldset className={estilos.opciones}>
        <legend className={estilos.etiqueta}>Modo de reserva</legend>
        {MODOS.map((modo) => (
          <label key={modo.valor} className={estilos.opcion}>
            <input type="radio" name="modo-reserva" value={modo.valor}
              checked={configuracion.modoReserva === modo.valor}
              onChange={() => cambiar('modoReserva', modo.valor)} />
            {modo.etiqueta}
          </label>
        ))}
      </fieldset>

      <div className={estilos.campo}>
        <label className={estilos.etiqueta} htmlFor="politica-cancelacion">Política de cancelación</label>
        <select id="politica-cancelacion" className={estilos.control} value={configuracion.politica}
          onChange={(evento) => cambiar('politica', evento.target.value)}>
          <option value="">Sin definir</option>
          {politicas.map((politica) => (
            <option key={politica.id} value={String(politica.id)}>{politica.nombre}</option>
          ))}
        </select>
        {politicaElegida && <p className={estilos.nota}>{politicaElegida.descripcion}</p>}
        {errorPoliticas && <p role="alert" className={estilos.error}>No pudimos cargar las políticas de cancelación.</p>}
      </div>

      <h3 className={estilos.subtitulo}>Servicios adicionales</h3>
      {configuracion.serviciosAdicionales.length === 0 ? (
        <p className={estilos.nota}>Todavía no hay servicios adicionales.</p>
      ) : (
        <ul className={estilos.lista} aria-label="Servicios adicionales">
          {configuracion.serviciosAdicionales.map((adicional, indice) => (
            <li key={`${indice}-${adicional.nombre}`} className={estilos.adicional}>
              <span>{adicional.nombre} · ${adicional.costo.toLocaleString('es-CO')}</span>
              <button type="button" className={estilos.botonSecundario} onClick={() => quitar(indice)}
                aria-label={`Quitar ${adicional.nombre}`}>Quitar</button>
            </li>
          ))}
        </ul>
      )}
      <form className={estilos.agregar} onSubmit={agregar} noValidate>
        <Campo id="nombre-adicional" etiqueta="Nombre del servicio adicional" value={nuevo.nombre}
          error={erroresNuevo.nombre} onChange={(evento) => setNuevo({ ...nuevo, nombre: evento.target.value })} />
        <Campo id="costo-adicional" etiqueta="Costo del servicio adicional (COP)" inputMode="decimal"
          value={nuevo.costo} error={erroresNuevo.costo}
          onChange={(evento) => setNuevo({ ...nuevo, costo: evento.target.value })} />
        <button type="submit" className={estilos.botonSecundario}>Agregar servicio</button>
      </form>

      {sinGuardar && <p className={estilos.nota}>Hay cambios en las condiciones sin guardar.</p>}
      {mensaje && (
        <p role={mensaje.error ? 'alert' : 'status'} className={mensaje.error ? estilos.error : estilos.aviso}>
          {mensaje.texto}
        </p>
      )}
      <button type="button" className={estilos.boton} onClick={guardar} disabled={enviando}>
        Guardar condiciones
      </button>
    </section>
  );
}

ConfiguracionPublicacion.propTypes = {
  publicacionId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
  publicacion: PropTypes.shape({
    tarifaLimpieza: PropTypes.number,
    modoReserva: PropTypes.oneOf(['INMEDIATA', 'APROBACION']).isRequired,
    politicaCancelacion: PropTypes.shape({ id: PropTypes.number }),
    serviciosAdicionales: PropTypes.arrayOf(PropTypes.shape({
      nombre: PropTypes.string, costo: PropTypes.number,
    })).isRequired,
  }).isRequired,
  alGuardar: PropTypes.func.isRequired,
};
