import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import PropTypes from 'prop-types';
import CotizadorEstancia from '../../cotizacion/components/CotizadorEstancia.jsx';
import ResenasPublicacion from '../../resenas/components/ResenasPublicacion.jsx';
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

// Íconos decorativos de la página: el texto de al lado ya dice lo que significan.
function Icono({ trazo }) {
  return (
    <svg className={estilos.icono} viewBox="0 0 24 24" aria-hidden="true">
      <path d={trazo} />
    </svg>
  );
}

Icono.propTypes = { trazo: PropTypes.string.isRequired };

const FLECHA = 'M19 12H5M11 18l-6-6 6-6';
const UBICACION = 'M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21zM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z';
const ESTRELLA = 'M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z';
const CHECK = 'M5 12.5l4.5 4.5L19 7';
const CALENDARIO = 'M7 3v3M17 3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z';
const ESCUDO = 'M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6z';

function EnlaceVolver() {
  return (
    <Link to="/huesped/busqueda" className={estilos.volver}>
      <Icono trazo={FLECHA} />
      Volver a la búsqueda
    </Link>
  );
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
        <EnlaceVolver />
      </main>
    );
  }

  if (estado === 'error') {
    return (
      <main className={estilos.pagina}>
        <p role="alert" className={estilos.error}>{MENSAJE_ERROR}</p>
        <EnlaceVolver />
      </main>
    );
  }

  return (
    <main className={estilos.pagina}>
      <EnlaceVolver />
      <header className={estilos.cabecera}>
        <h1 className={estilos.titulo}>{detalle.titulo}</h1>
        <div className={estilos.datos}>
          <p className={estilos.dato}>
            <Icono trazo={UBICACION} />
            {[detalle.tipo, detalle.ciudad].filter(Boolean).join(' · ')}
            {detalle.capacidad != null && ` · Hasta ${detalle.capacidad} huéspedes`}
          </p>
          <p className={`${estilos.dato} ${detalle.calificacionPromedio == null ? '' : estilos.calificacion}`}>
            <Icono trazo={ESTRELLA} />
            {detalle.calificacionPromedio == null
              ? 'Aún sin calificaciones'
              : `Calificación promedio: ${detalle.calificacionPromedio.toFixed(1)} de 5`}
          </p>
          {detalle.precioNoche != null && (
            <p className={estilos.precio}>{FORMATO_PESOS.format(detalle.precioNoche)} por noche</p>
          )}
        </div>
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
                {detalle.servicios.map((servicio) => (
                  <li key={servicio} className={estilos.servicio}>
                    <Icono trazo={CHECK} />
                    {servicio}
                  </li>
                ))}
              </ul>
            </section>
          )}

          {detalle.reglas && (
            <section className={estilos.bloque}>
              <h2 className={estilos.seccion}>Reglas</h2>
              <p className={estilos.tarjetaTexto}>{detalle.reglas}</p>
            </section>
          )}

          {detalle.politicaCancelacion && (
            <section className={estilos.bloque}>
              <h2 className={estilos.seccion}>Política de cancelación</h2>
              <div className={estilos.politica}>
                <span className={estilos.politicaIcono}><Icono trazo={ESCUDO} /></span>
                <div>
                  <p><strong>{detalle.politicaCancelacion}</strong></p>
                  {detalle.descripcionPolitica && <p className={estilos.nota}>{detalle.descripcionPolitica}</p>}
                </div>
              </div>
            </section>
          )}

          <section className={estilos.bloque}>
            <h2 className={estilos.seccion}>Disponibilidad</h2>
            {detalle.periodos.length === 0 ? (
              <p className={estilos.nota}>El anfitrión aún no publicó fechas disponibles.</p>
            ) : (
              <ul className={estilos.periodos}>
                {detalle.periodos.map((periodo) => (
                  <li key={`${periodo.inicio}-${periodo.fin}`} className={estilos.periodo}>
                    <Icono trazo={CALENDARIO} />
                    Del {formatearFecha(periodo.inicio)} al {formatearFecha(periodo.fin)}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className={estilos.bloque}>
            <h2 className={estilos.seccion}>Reseñas</h2>
            <ResenasPublicacion publicacionId={detalle.id} />
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
