import PropTypes from 'prop-types';
import estilos from './PasosPublicacion.module.css';

/** Los seis pasos del asistente; `campos` son los datos de la API (camposFaltantes) que completa cada uno. */
export const PASOS = [
  { numero: 1, titulo: 'Información básica', campos: ['descripcion', 'tipo', 'capacidad'] },
  { numero: 2, titulo: 'Ubicación', campos: ['ciudad', 'direccion'] },
  { numero: 3, titulo: 'Precio y tarifas', campos: ['precioNoche'] },
  { numero: 4, titulo: 'Servicios y reglas', campos: ['servicios', 'reglas'] },
  { numero: 5, titulo: 'Imágenes', campos: ['imagenes'] },
  { numero: 6, titulo: 'Reserva y cancelación', campos: [] },
];

// Lo que pide la activación, en el orden en que se le muestra al anfitrión.
const REQUISITOS = [
  { etiqueta: 'Título y descripción', falta: 'Falta la descripción', campos: ['descripcion'] },
  { etiqueta: 'Tipo y capacidad', falta: 'Falta el tipo o la capacidad', campos: ['tipo', 'capacidad'] },
  { etiqueta: 'Precio por noche', falta: 'Falta el precio por noche', campos: ['precioNoche'] },
  { etiqueta: 'Servicios y reglas', falta: 'Faltan los servicios o las reglas', campos: ['servicios', 'reglas'] },
  { etiqueta: 'Ubicación', falta: 'Falta la dirección', campos: ['ciudad', 'direccion'] },
  { etiqueta: 'Al menos una imagen', falta: 'Falta al menos una imagen', campos: ['imagenes'] },
];

function Marca({ tipo, numero }) {
  if (tipo === 'actual') return <span className={`${estilos.indicador} ${estilos.actual}`}>{numero}</span>;
  if (tipo === 'falta') return <span className={`${estilos.indicador} ${estilos.falta}`} aria-hidden="true">!</span>;
  if (tipo === 'completo') return <span className={`${estilos.indicador} ${estilos.completo}`} aria-hidden="true">✓</span>;
  return <span className={`${estilos.indicador} ${estilos.pendiente}`}>{numero}</span>;
}

Marca.propTypes = { tipo: PropTypes.string.isRequired, numero: PropTypes.number.isRequired };

/**
 * Lista de pasos y requisitos de activación. `faltantes` es null mientras la publicación no existe:
 * entonces no se juzga nada como completo ni como faltante.
 */
export default function PasosPublicacion({ paso, faltantes, alIr }) {
  const conocido = faltantes !== null;
  const estaFaltante = (campos) => conocido && campos.some((campo) => faltantes.includes(campo));

  return (
    <aside className={estilos.lateral}>
      <nav aria-label="Pasos de la publicación">
        <ol className={estilos.pasos}>
          {PASOS.map((item) => {
            let tipo = 'pendiente';
            if (item.numero === paso) tipo = 'actual';
            else if (estaFaltante(item.campos)) tipo = 'falta';
            else if (conocido) tipo = 'completo';
            return (
              <li key={item.numero}>
                <button type="button" className={`${estilos.paso} ${item.numero === paso ? estilos.pasoActual : ''}`}
                  aria-current={item.numero === paso ? 'step' : undefined} onClick={() => alIr(item.numero)}>
                  <Marca tipo={tipo} numero={item.numero} />
                  {item.titulo}
                </button>
              </li>
            );
          })}
        </ol>
      </nav>

      <section className={estilos.requisitos} aria-labelledby="titulo-requisitos">
        <h2 id="titulo-requisitos" className={estilos.tituloRequisitos}>Antes de solicitar la activación</h2>
        <p className={estilos.texto}>Una publicación necesita la información mínima completa para poder activarse.</p>
        <ul className={estilos.lista}>
          {REQUISITOS.map((requisito) => {
            const falta = estaFaltante(requisito.campos);
            return (
              <li key={requisito.etiqueta} className={falta ? estilos.faltaItem : estilos.item}>
                <span className={`${estilos.casilla} ${falta ? estilos.casillaFalta : ''} ${conocido ? '' : estilos.casillaNeutra}`}
                  aria-hidden="true">
                  {falta ? '!' : conocido && '✓'}
                </span>
                {falta ? requisito.falta : requisito.etiqueta}
              </li>
            );
          })}
        </ul>
      </section>
      <p className={estilos.nota}>
        Al solicitar la activación, un administrador revisa la publicación antes de que aparezca en las búsquedas.
      </p>
    </aside>
  );
}

PasosPublicacion.propTypes = {
  paso: PropTypes.number.isRequired,
  faltantes: PropTypes.arrayOf(PropTypes.string),
  alIr: PropTypes.func.isRequired,
};
