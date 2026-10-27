import { Link } from 'react-router-dom';
import estilos from './Encabezado.module.css';

/** Encabezado común de todas las páginas con la marca y el enlace al inicio. */
export default function Encabezado() {
  return (
    <header className={estilos.encabezado}>
      <Link to="/" className={estilos.marca} aria-label="Inversiones LR, ir al inicio">
        <span className={estilos.sello} aria-hidden="true">LR</span>
        Inversiones LR
      </Link>
    </header>
  );
}
