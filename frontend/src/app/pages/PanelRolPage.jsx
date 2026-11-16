import { Link } from 'react-router-dom';
import ResumenRevision from '../../modules/alojamientos/components/ResumenRevision.jsx';
import { useSesion } from '../../shared/sesion/useSesion.js';
import estilos from './PanelRolPage.module.css';

const NOMBRE_ROL = {
  HUESPED: 'Huésped',
  ANFITRION: 'Anfitrión',
  ADMINISTRADOR: 'Administrador',
};

// Herramientas del administrador; cada módulo aporta su tarjeta y aquí solo se componen.
function HerramientasAdministrador() {
  return (
    <section aria-labelledby="herramientas-titulo">
      <h2 id="herramientas-titulo" className={estilos.subtitulo}>Tus herramientas</h2>
      <div className={estilos.herramientas}>
        <ResumenRevision />
        <section className={estilos.tarjeta} aria-labelledby="cuenta-titulo">
          <h3 id="cuenta-titulo" className={estilos.tituloTarjeta}>Tu cuenta</h3>
          <p className={estilos.nota}>Consulta y actualiza tus datos o cambia tu contraseña.</p>
          <Link to="/mi-cuenta" className={estilos.enlace}>Ir a Mi cuenta</Link>
        </section>
      </div>
    </section>
  );
}

// Panel de /huesped, /anfitrion y /administrador. Las rutas ya validan que hay sesión,
// pero puede desaparecer mientras el panel sigue montado (logout o expiración).
export default function PanelRolPage() {
  const sesion = useSesion();
  if (!sesion) return null;
  const { usuario } = sesion;
  const esAdministrador = usuario.rol === 'ADMINISTRADOR';

  return (
    <main className={esAdministrador ? estilos.paginaAmplia : estilos.pagina}>
      <h1 className={estilos.titulo}>Hola, {usuario.nombre}</h1>
      <p className={estilos.rol}>Ingresaste como {NOMBRE_ROL[usuario.rol] ?? usuario.rol}.</p>
      {esAdministrador ? (
        <HerramientasAdministrador />
      ) : (
        <>
          <p className={estilos.nota}>Tu panel está en construcción. Pronto verás aquí tus herramientas.</p>
          {usuario.rol === 'HUESPED' && (
            <Link to="/huesped/busqueda" className={estilos.enlace}>Buscar alojamiento</Link>
          )}
        </>
      )}
    </main>
  );
}
