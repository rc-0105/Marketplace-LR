import { Link, Route, Routes, useNavigate } from 'react-router-dom';
import { cerrarSesion } from '../modules/identidad/services/identidadService.js';
import InicioSesionPage from '../modules/identidad/pages/InicioSesionPage.jsx';
import RecuperacionPage from '../modules/identidad/pages/RecuperacionPage.jsx';
import MiCuentaPage from '../modules/identidad/pages/MiCuentaPage.jsx';
import RegistroPage from '../modules/identidad/pages/RegistroPage.jsx';
import PanelRolPage from './pages/PanelRolPage.jsx';
import Encabezado from '../shared/components/Encabezado.jsx';
import ProveedorSesion from '../shared/sesion/ProveedorSesion.jsx';
import RutaProtegida from '../shared/sesion/RutaProtegida.jsx';
import SoloInvitados from '../shared/sesion/SoloInvitados.jsx';

const ROLES_CON_PANEL = [
  { rol: 'HUESPED', ruta: '/huesped' },
  { rol: 'ANFITRION', ruta: '/anfitrion' },
  { rol: 'ADMINISTRADOR', ruta: '/administrador' },
];

function Inicio() {
  return (
    <main>
      <h1>Marketplace de Alojamientos</h1>
      <p>Inversiones LR · interfaz en construcción.</p>
      <Link to="/registro">Crear una cuenta</Link>
    </main>
  );
}

export default function App() {
  const navigate = useNavigate();

  async function alCerrarSesion() {
    await cerrarSesion();
    navigate('/inicio-sesion', { replace: true });
  }

  return (
    <ProveedorSesion>
      <Encabezado alCerrarSesion={alCerrarSesion} />
      <Routes>
        <Route path="/" element={<Inicio />} />
        <Route path="/registro" element={<SoloInvitados><RegistroPage /></SoloInvitados>} />
        <Route path="/inicio-sesion" element={<SoloInvitados><InicioSesionPage /></SoloInvitados>} />
        <Route path="/recuperar-contrasena" element={<SoloInvitados><RecuperacionPage /></SoloInvitados>} />
        <Route path="/mi-cuenta" element={<RutaProtegida><MiCuentaPage /></RutaProtegida>} />
        {ROLES_CON_PANEL.map(({ rol, ruta }) => (
          <Route key={ruta} path={ruta} element={<RutaProtegida rol={rol}><PanelRolPage /></RutaProtegida>} />
        ))}
      </Routes>
    </ProveedorSesion>
  );
}
