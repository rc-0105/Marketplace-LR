import PropTypes from 'prop-types';
import portada from '../../assets/inicio/portada.png';
import estilos from './PantallaAcceso.module.css';

/**
 * Pantalla de acceso (inicio de sesión y registro): foto de fondo, el título a la izquierda y el
 * formulario a la derecha. Los campos del formulario toman aquí el estilo de líneas sobre la foto.
 * Al abrirse, el contenido se desliza desde `entrada` mientras la foto queda quieta: pasar del inicio de sesión
 * al registro se siente como un solo marco que cambia de contenido.
 */
export default function PantallaAcceso({ titulo, entrada = 'derecha', children }) {
  return (
    <main className={estilos.pagina}>
      <div className={`${estilos.marco} ${estilos[entrada]}`} style={{ backgroundImage: `url(${portada})` }}>
        <div className={estilos.velo} />
        <div className={estilos.titulo}>
          <h1 className={estilos.encabezado}>{titulo}</h1>
          <p className={estilos.marca}>Marketplace LR</p>
        </div>
        <div className={estilos.formulario}>{children}</div>
      </div>
    </main>
  );
}

PantallaAcceso.propTypes = {
  titulo: PropTypes.string.isRequired,
  entrada: PropTypes.oneOf(['izquierda', 'derecha']),
  children: PropTypes.node.isRequired,
};
