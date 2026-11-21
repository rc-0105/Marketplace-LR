import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import CotizadorEstancia from '../../cotizacion/components/CotizadorEstancia.jsx';
import { consultarDetalle } from '../services/busquedaService.js';
import estilos from './DetalleAlojamientoPage.module.css';

const MENSAJE_ERROR = 'No pudimos cargar el alojamiento. Intenta de nuevo.';
const FORMATO_PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const FORMATO_FECHA = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'long', year: 'numeric' });

// Las fechas llegan como AAAA-MM-DD: se leen como fecha local para que no se corran un día.
function formatearFecha(fecha) {
  return FORMATO_FECHA.format(new Date(`${fecha}T00:00:00`));
}

// La búsqueda manda sus fechas en la dirección; si no vienen o no son AAAA-MM-DD, el cotizador empieza vacío.
function fechaDeLaBusqueda(valor) {
  return /^\d{4}-\d{2}-\d{2}$/.test(valor ?? '') ? valor : '';
}

export default function DetalleAlojamientoPage() {
  const { id } = useParams();
  const [consulta] = useSearchParams();
  const [detalle, setDetalle] = useState(null);
  const [estado, setEstado] = useState('cargando');

  useEffect(() => {
    let vigente = true;
    setEstado('cargando');
    consultarDetalle(id)
      .then((datos) => {
        if (!vigente) return;
        setDetalle(datos);
        setEstado('listo');
      })
      .catch((error) => {
        if (vigente) setEstado(error.estado === 404 ? 'no-disponible' : 'error');
      });
    return () => { vigente = false; };
  }, [id]);

  if (estado === 'cargando') {
    return <main className={estilos.pagina}><p>Cargando alojamiento…</p></main>;
  }

  if (estado === 'no-disponible') {
    return (
      <main className={estilos.pagina}>
        <h1 className={estilos.titulo}>Alojamiento no disponible</h1>
        <p className={estilos.aviso}>Este alojamiento ya no está disponible o no existe.</p>
        <Link to="/huesped/busqueda">Volver a la búsqueda</Link>
      </main>
    );
  }

  if (estado === 'error') {
    return (
      <main className={estilos.pagina}>
        <p role="alert" className={estilos.error}>{MENSAJE_ERROR}</p>
        <Link to="/huesped/busqueda">Volver a la búsqueda</Link>
      </main>
    );
  }

  return (
    <main className={estilos.pagina}>
      <Link to="/huesped/busqueda" className={estilos.volver}>Volver a la búsqueda</Link>
      <header className={estilos.cabecera}>
        <h1 className={estilos.titulo}>{detalle.titulo}</h1>
        <p className={estilos.detalle}>
          {[detalle.tipo, detalle.ciudad].filter(Boolean).join(' · ')}
          {detalle.capacidad != null && ` · Hasta ${detalle.capacidad} huéspedes`}
        </p>
        <p className={estilos.detalle}>
          {detalle.calificacionPromedio == null
            ? 'Aún sin calificaciones'
            : `Calificación promedio: ${detalle.calificacionPromedio.toFixed(1)} de 5`}
        </p>
        {detalle.precioNoche != null && (
          <p className={estilos.precio}>{FORMATO_PESOS.format(detalle.precioNoche)} por noche</p>
        )}
      </header>

      {detalle.imagenes.length > 0 && (
        <ul className={estilos.imagenes}>
          {detalle.imagenes.map((url, posicion) => (
            <li key={url} className={estilos.foto}>
              <img src={url} alt={`Foto ${posicion + 1} de ${detalle.titulo}`} className={estilos.imagen} />
            </li>
          ))}
        </ul>
      )}

      <div className={estilos.columnas}>
        <div className={estilos.informacion}>
          {detalle.descripcion && (
            <section className={estilos.bloque}>
              <h2 className={estilos.seccion}>Descripción</h2>
              <p className={estilos.descripcion}>{detalle.descripcion}</p>
            </section>
          )}

          {detalle.servicios.length > 0 && (
            <section className={estilos.bloque}>
              <h2 className={estilos.seccion}>Servicios</h2>
              <ul className={estilos.servicios}>
                {detalle.servicios.map((servicio) => <li key={servicio}>{servicio}</li>)}
              </ul>
            </section>
          )}

          {detalle.reglas && (
            <section className={estilos.bloque}>
              <h2 className={estilos.seccion}>Reglas</h2>
              <p>{detalle.reglas}</p>
            </section>
          )}

          {detalle.politicaCancelacion && (
            <section className={estilos.bloque}>
              <h2 className={estilos.seccion}>Política de cancelación</h2>
              <p><strong>{detalle.politicaCancelacion}</strong></p>
              {detalle.descripcionPolitica && <p>{detalle.descripcionPolitica}</p>}
            </section>
          )}

          <section className={estilos.bloque}>
            <h2 className={estilos.seccion}>Disponibilidad</h2>
            {detalle.periodos.length === 0 ? (
              <p>El anfitrión aún no publicó fechas disponibles.</p>
            ) : (
              <ul className={estilos.periodos}>
                {detalle.periodos.map((periodo) => (
                  <li key={`${periodo.inicio}-${periodo.fin}`}>
                    Del {formatearFecha(periodo.inicio)} al {formatearFecha(periodo.fin)}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <aside className={estilos.cotizacion}>
          <CotizadorEstancia publicacionId={detalle.id} serviciosAdicionales={detalle.serviciosAdicionales}
            llegadaInicial={fechaDeLaBusqueda(consulta.get('llegada'))}
            salidaInicial={fechaDeLaBusqueda(consulta.get('salida'))} />
        </aside>
      </div>
    </main>
  );
}
