import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listarPendientesDeRevision } from '../services/alojamientosService.js';
import estilos from './ResumenRevision.module.css';

const MENSAJE_ERROR = 'No pudimos consultar las publicaciones pendientes.';

function resumenDe(cantidad) {
  if (cantidad === 0) {
    return 'No hay publicaciones pendientes de revisión.';
  }
  return cantidad === 1
    ? '1 publicación espera tu revisión.'
    : `${cantidad} publicaciones esperan tu revisión.`;
}

/** Tarjeta del panel del administrador: cuántas publicaciones esperan revisión y el acceso para revisarlas. */
export default function ResumenRevision() {
  const [cantidad, setCantidad] = useState(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let vigente = true;
    listarPendientesDeRevision()
      .then((pendientes) => vigente && setCantidad(pendientes.length))
      .catch(() => vigente && setError(true));
    // Si el panel se desmonta antes de la respuesta, no se toca el estado.
    return () => {
      vigente = false;
    };
  }, []);

  let texto = 'Consultando publicaciones pendientes…';
  if (error) {
    texto = MENSAJE_ERROR;
  } else if (cantidad !== null) {
    texto = resumenDe(cantidad);
  }

  return (
    <section className={estilos.tarjeta} aria-labelledby="resumen-revision-titulo">
      <h3 id="resumen-revision-titulo" className={estilos.titulo}>Revisión de publicaciones</h3>
      <p className={cantidad > 0 ? estilos.pendientes : estilos.texto}>{texto}</p>
      <Link to="/administrador/publicaciones" className={estilos.enlace}>Revisar publicaciones</Link>
    </section>
  );
}
