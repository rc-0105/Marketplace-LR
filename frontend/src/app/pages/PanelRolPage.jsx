import { useSesion } from '../../shared/sesion/useSesion.js';
import estilos from './PanelRolPage.module.css';

const NOMBRE_ROL = {
  HUESPED: 'Huésped',
  ANFITRION: 'Anfitrión',
  ADMINISTRADOR: 'Administrador',
};

// Panel provisional de /huesped, /anfitrion y /administrador. Las rutas ya validan que hay sesión.
export default function PanelRolPage() {
  const { usuario } = useSesion();

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Hola, {usuario.nombre}</h1>
      <p className={estilos.rol}>Ingresaste como {NOMBRE_ROL[usuario.rol] ?? usuario.rol}.</p>
      <p className={estilos.nota}>Tu panel está en construcción. Pronto verás aquí tus herramientas.</p>
    </main>
  );
}
