import { Link } from 'react-router-dom';
import ResumenRevision from '../../modules/alojamientos/components/ResumenRevision.jsx';
import Icono from '../../shared/components/Icono.jsx';
import { useSesion } from '../../shared/sesion/useSesion.js';
import tarjetas from '../../shared/styles/TarjetaHerramienta.module.css';
import InicioHuespedPage from './InicioHuespedPage.jsx';
import estilos from './PanelRolPage.module.css';

const NOMBRE_ROL = {
  HUESPED: 'Huésped',
  ANFITRION: 'Anfitrión',
  ADMINISTRADOR: 'Administrador',
};

// Las demás herramientas del administrador; la de revisión la aporta su módulo (ResumenRevision).
const HERRAMIENTAS = [
  {
    id: 'moderacion', icono: 'moderacion', titulo: 'Moderación',
    nota: 'Bloquea publicaciones engañosas y resuelve las reservas que quedan en revisión.',
    ruta: '/administrador/moderacion', enlace: 'Moderar publicaciones',
  },
  {
    id: 'usuarios', icono: 'usuarios', titulo: 'Usuarios',
    nota: 'Restringe las cuentas que incumplen las políticas o rehabilítalas.',
    ruta: '/administrador/usuarios', enlace: 'Administrar usuarios',
  },
  {
    id: 'resenas', icono: 'resenas', titulo: 'Reseñas',
    nota: 'Oculta las reseñas que incumplen las políticas de la plataforma.',
    ruta: '/administrador/resenas', enlace: 'Moderar reseñas',
  },
  {
    id: 'auditoria', icono: 'auditoria', titulo: 'Auditoría',
    nota: 'Consulta los eventos críticos registrados en la plataforma.',
    ruta: '/administrador/auditoria', enlace: 'Consultar auditoría',
  },
  {
    id: 'cuenta', icono: 'cuenta', titulo: 'Tu cuenta',
    nota: 'Consulta y actualiza tus datos o cambia tu contraseña.',
    ruta: '/mi-cuenta', enlace: 'Ir a Mi cuenta',
  },
];

function HerramientasAdministrador() {
  return (
    <section aria-labelledby="herramientas-titulo">
      <h2 id="herramientas-titulo" className={estilos.subtitulo}>Tus herramientas</h2>
      <div className={estilos.herramientas}>
        <ResumenRevision />
        {HERRAMIENTAS.map((herramienta) => (
          <section key={herramienta.id} className={tarjetas.tarjeta} aria-labelledby={`${herramienta.id}-titulo`}>
            <span className={tarjetas.icono}><Icono nombre={herramienta.icono} /></span>
            <h3 id={`${herramienta.id}-titulo`} className={tarjetas.titulo}>{herramienta.titulo}</h3>
            <p className={tarjetas.nota}>{herramienta.nota}</p>
            <Link to={herramienta.ruta} className={tarjetas.enlace}>
              {herramienta.enlace}
              <Icono nombre="flecha" />
            </Link>
          </section>
        ))}
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
  if (usuario.rol === 'HUESPED') return <InicioHuespedPage nombre={usuario.nombre} />;
  const esAdministrador = usuario.rol === 'ADMINISTRADOR';

  return (
    <main className={esAdministrador ? estilos.paginaAmplia : estilos.pagina}>
      <header className={estilos.cabecera}>
        <h1 className={estilos.titulo}>Hola, {usuario.nombre}</h1>
        <p className={estilos.rol}>Ingresaste como {NOMBRE_ROL[usuario.rol] ?? usuario.rol}.</p>
      </header>
      {esAdministrador ? (
        <HerramientasAdministrador />
      ) : (
        <p className={estilos.nota}>Tu panel está en construcción. Pronto verás aquí tus herramientas.</p>
      )}
    </main>
  );
}
