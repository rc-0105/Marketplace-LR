import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import FiltrosAuditoria from '../components/FiltrosAuditoria.jsx';
import { formatearFechaBogota } from '../models/eventoAuditoria.js';
import { listar } from '../services/auditoriaService.js';
import estilos from './AuditoriaPage.module.css';

const MENSAJE_ERROR = 'No pudimos cargar los eventos de auditoría. Intenta de nuevo.';
const NOMBRES_FILTRO = ['usuario', 'evento', 'entidad', 'entidadId', 'desde', 'hasta'];
const NOMBRE_RESULTADO = { EXITO: 'Éxito', FALLO: 'Fallo' };

function filtrosDe(parametros) {
  return Object.fromEntries(NOMBRES_FILTRO.filter((nombre) => parametros.get(nombre)).map((nombre) => [nombre, parametros.get(nombre)]));
}

function paginaDe(parametros) {
  const numero = Number(parametros.get('pagina'));
  return Number.isInteger(numero) && numero > 0 ? numero : 0;
}

/** Consulta de solo lectura del registro de auditoría para el administrador (RF-59). */
export default function AuditoriaPage() {
  // Filtros y página viven en la dirección: se puede compartir y el detalle vuelve con la misma búsqueda.
  const [parametros, setParametros] = useSearchParams();
  const filtros = filtrosDe(parametros);
  const clave = parametros.toString();
  const pagina = paginaDe(parametros);
  const [resultado, setResultado] = useState(null);
  const [error, setError] = useState(null);

  // Cada carga lleva un número: solo la última aplica su respuesta, y ninguna tras desmontar la página.
  const ultimaCarga = useRef(0);

  const cargar = useCallback(async () => {
    const numero = ++ultimaCarga.current;
    const consulta = new URLSearchParams(clave);
    setError(null);
    setResultado(null);
    try {
      const datos = await listar(filtrosDe(consulta), paginaDe(consulta), 50);
      if (numero === ultimaCarga.current) setResultado(datos);
    } catch (fallo) {
      // Solo un 400 trae un mensaje pensado para el administrador; el resto va con el genérico.
      if (numero === ultimaCarga.current) setError(fallo.estado === 400 ? fallo.message : MENSAJE_ERROR);
    }
  }, [clave]);

  useEffect(() => {
    cargar();
    return () => {
      ultimaCarga.current += 1;
    };
  }, [cargar]);

  const eventos = resultado?.contenido ?? [];
  const totalPaginas = resultado?.totalPaginas ?? 0;

  function aplicar(nuevos) {
    setParametros(new URLSearchParams(nuevos));
  }

  function irAPagina(destino) {
    setParametros(new URLSearchParams({ ...filtros, ...(destino > 0 && { pagina: destino }) }));
  }

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Auditoría</h1>
      <p className={estilos.nota}>Eventos críticos registrados en la plataforma, del más reciente al más antiguo.</p>
      <FiltrosAuditoria key={JSON.stringify(filtros)} valores={filtros} alAplicar={aplicar} />
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
                  <td>
                    <Link to={`/administrador/auditoria/${evento.id}${clave && `?${clave}`}`}
                      aria-label={`${formatearFechaBogota(evento.fecha)}, evento ${evento.id}`}>
                      {formatearFechaBogota(evento.fecha)}
                    </Link>
                  </td>
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
      <nav className={estilos.paginacion} aria-label="Paginación">
        <button type="button" className={estilos.secundario} disabled={pagina <= 0 || !!error}
          onClick={() => irAPagina(pagina - 1)}>
          Anterior
        </button>
        <span>{totalPaginas > 0 ? `Página ${pagina + 1} de ${totalPaginas}` : 'Sin páginas'}</span>
        <button type="button" className={estilos.secundario} disabled={pagina + 1 >= totalPaginas || !!error}
          onClick={() => irAPagina(pagina + 1)}>
          Siguiente
        </button>
      </nav>
    </main>
  );
}
