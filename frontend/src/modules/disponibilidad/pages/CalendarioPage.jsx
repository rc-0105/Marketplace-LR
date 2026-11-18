import { Link, useParams } from 'react-router-dom';
import CalendarioConsulta from '../components/CalendarioConsulta.jsx';
import estilos from './CalendarioPage.module.css';

/** Calendario de disponibilidad de una publicación, visto por el huésped (HU-15, RF-25). */
export default function CalendarioPage() {
  const { id } = useParams();

  return (
    <main className={estilos.pagina}>
      <Link to="/huesped/busqueda" className={estilos.volver}>Volver a la búsqueda</Link>
      <h1 className={estilos.titulo}>Calendario de disponibilidad</h1>
      <CalendarioConsulta key={id} publicacionId={id} />
    </main>
  );
}
