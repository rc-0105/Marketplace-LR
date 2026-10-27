import PropTypes from 'prop-types';
import { REQUISITOS_CONTRASENA } from '../models/usuario.js';
import estilos from './RequisitosContrasena.module.css';

/** Lista de requisitos de la contraseña que se marcan a medida que se cumplen. */
export default function RequisitosContrasena({ contrasena }) {
  return (
    <ul className={estilos.requisitos} aria-label="Requisitos de la contraseña">
      {REQUISITOS_CONTRASENA.map((requisito) => {
        const cumplido = requisito.cumple(contrasena);
        return (
          <li key={requisito.id} className={cumplido ? estilos.cumplido : estilos.pendiente}>
            <span className={estilos.marca} aria-hidden="true">{cumplido ? '✓' : '○'}</span>
            <span className={estilos.oculto}>{cumplido ? 'Cumplido: ' : 'Pendiente: '}</span>
            {requisito.texto}
          </li>
        );
      })}
    </ul>
  );
}

RequisitosContrasena.propTypes = {
  contrasena: PropTypes.string.isRequired,
};
