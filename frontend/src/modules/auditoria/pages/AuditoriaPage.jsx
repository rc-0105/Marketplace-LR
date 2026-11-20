import { useCallback, useEffect, useRef, useState } from 'react';
import { formatearFechaBogota } from '../models/eventoAuditoria.js';
import { listar } from '../services/auditoriaService.js';
import estilos from './AuditoriaPage.module.css';

const MENSAJE_ERROR = 'No pudimos cargar los eventos de auditoría. Intenta de nuevo.';
const NOMBRE_RESULTADO = { EXITO: 'Éxito', FALLO: 'Fallo' };

/** Consulta de solo lectura del registro de auditoría para el administrador (RF-59). */
export default function AuditoriaPage() {
  const [resultado, setResultado] = useState(null);
  const [error, setError] = useState(null);

  // Cada carga lleva un número: solo la última aplica su respuesta, y ninguna tras desmontar la página.
  const ultimaCarga = useRef(0);

  const cargar = useCallback(async () => {
    const numero = ++ultimaCarga.current;
    setError(null);
    try {
      const datos = await listar({}, 0, 50);
      if (numero === ultimaCarga.current) setResultado(datos);
    } catch (fallo) {
      // Solo un 400 trae un mensaje pensado para el administrador; el resto va con el genérico.
      if (numero === ultimaCarga.current) setError(fallo.estado === 400 ? fallo.message : MENSAJE_ERROR);
    }
  }, []);

  useEffect(() => {
    cargar();
    return () => {
      ultimaCarga.current += 1;
    };
  }, [cargar]);

  const eventos = resultado?.contenido ?? [];

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Auditoría</h1>
      <p className={estilos.nota}>Eventos críticos registrados en la plataforma, del más reciente al más antiguo.</p>
      {error && <p role="alert" className={estilos.error}>{error}</p>}
      {!error && resultado === null && <output className={estilos.nota}>Cargando eventos…</output>}
      {!error && resultado !== null && eventos.length === 0 && (
        <output className={estilos.nota}>Sin resultados: no hay eventos de auditoría con esos criterios.</output>
      )}
      {!error && eventos.length > 0 && (
        <div className={estilos.contenedorTabla}>
          <table className={estilos.tabla}>
            <caption className={estilos.caption}>Eventos de auditoría</caption>
            <thead>
              <tr>
                <th scope="col">Fecha (Bogotá)</th>
                <th scope="col">Usuario</th>
                <th scope="col">Evento</th>
                <th scope="col">Entidad</th>
                <th scope="col">Id de la entidad</th>
                <th scope="col">Resultado</th>
              </tr>
            </thead>
            <tbody>
              {eventos.map((evento) => (
                <tr key={evento.id}>
                  <td>{formatearFechaBogota(evento.fecha)}</td>
                  <td>{evento.usuario ?? 'Sin usuario'}</td>
                  <td>{evento.accion}</td>
                  <td>{evento.entidad}</td>
                  <td>{evento.entidadId ?? 'Sin id'}</td>
                  <td>{NOMBRE_RESULTADO[evento.resultado] ?? evento.resultado}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}
