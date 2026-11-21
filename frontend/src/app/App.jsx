import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { cerrarSesion } from '../modules/identidad/services/identidadService.js';
import InicioSesionPage from '../modules/identidad/pages/InicioSesionPage.jsx';
import RecuperacionPage from '../modules/identidad/pages/RecuperacionPage.jsx';
import MiCuentaPage from '../modules/identidad/pages/MiCuentaPage.jsx';
import AdministracionUsuariosPage from '../modules/identidad/pages/AdministracionUsuariosPage.jsx';
import RegistroPage from '../modules/identidad/pages/RegistroPage.jsx';
import MisPublicacionesPage from '../modules/alojamientos/pages/MisPublicacionesPage.jsx';
import ModeracionPage from '../modules/alojamientos/pages/ModeracionPage.jsx';
import PublicacionEnviadaPage from '../modules/alojamientos/pages/PublicacionEnviadaPage.jsx';
import PublicacionFormPage from '../modules/alojamientos/pages/PublicacionFormPage.jsx';
import RevisionPublicacionesPage from '../modules/alojamientos/pages/RevisionPublicacionesPage.jsx';
import AuditoriaPage from '../modules/auditoria/pages/AuditoriaPage.jsx';
import DetalleEventoAuditoriaPage from '../modules/auditoria/pages/DetalleEventoAuditoriaPage.jsx';
import BusquedaPage from '../modules/busqueda/pages/BusquedaPage.jsx';
import DetalleAlojamientoPage from '../modules/busqueda/pages/DetalleAlojamientoPage.jsx';
import CalendarioPage from '../modules/disponibilidad/pages/CalendarioPage.jsx';
import DisponibilidadPage from '../modules/disponibilidad/pages/DisponibilidadPage.jsx';
import DetalleReservaPage from '../modules/reservas/pages/DetalleReservaPage.jsx';
import MisReservasPage from '../modules/reservas/pages/MisReservasPage.jsx';
import PortalPagoPage from '../modules/reservas/pages/PortalPagoPage.jsx';
import ModeracionResenasPage from '../modules/resenas/pages/ModeracionResenasPage.jsx';
import NotificacionesPage from '../modules/notificaciones/pages/NotificacionesPage.jsx';
import InicioPage from './pages/InicioPage.jsx';
import PanelRolPage from './pages/PanelRolPage.jsx';
import Encabezado from '../shared/components/Encabezado.jsx';
import ProveedorSesion from '../shared/sesion/ProveedorSesion.jsx';
import RutaProtegida from '../shared/sesion/RutaProtegida.jsx';
import SoloInvitados from '../shared/sesion/SoloInvitados.jsx';

const ROLES_CON_PANEL = [
  { rol: 'HUESPED', ruta: '/huesped' },
  { rol: 'ADMINISTRADOR', ruta: '/administrador' },
];

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
        <Route path="/" element={<InicioPage />} />
        <Route path="/registro" element={<SoloInvitados><RegistroPage /></SoloInvitados>} />
        <Route path="/inicio-sesion" element={<SoloInvitados><InicioSesionPage /></SoloInvitados>} />
        <Route path="/recuperar-contrasena" element={<SoloInvitados><RecuperacionPage /></SoloInvitados>} />
        <Route path="/mi-cuenta" element={<RutaProtegida><MiCuentaPage /></RutaProtegida>} />
        <Route path="/notificaciones" element={<RutaProtegida><NotificacionesPage /></RutaProtegida>} />
        {/* Mis publicaciones es el inicio del anfitrión: /anfitrion lleva directo ahí. */}
        <Route path="/anfitrion"
          element={<RutaProtegida rol="ANFITRION"><Navigate to="/anfitrion/publicaciones" replace /></RutaProtegida>} />
        <Route path="/anfitrion/publicaciones"
          element={<RutaProtegida rol="ANFITRION"><MisPublicacionesPage /></RutaProtegida>} />
        <Route path="/anfitrion/publicaciones/nueva"
          element={<RutaProtegida rol="ANFITRION"><PublicacionFormPage /></RutaProtegida>} />
        <Route path="/anfitrion/publicaciones/:id/editar"
          element={<RutaProtegida rol="ANFITRION"><PublicacionFormPage /></RutaProtegida>} />
        <Route path="/anfitrion/publicaciones/:id/enviada"
          element={<RutaProtegida rol="ANFITRION"><PublicacionEnviadaPage /></RutaProtegida>} />
        <Route path="/anfitrion/publicaciones/:id/disponibilidad"
          element={<RutaProtegida rol="ANFITRION"><DisponibilidadPage /></RutaProtegida>} />
        <Route path="/huesped/busqueda"
          element={<RutaProtegida rol="HUESPED"><BusquedaPage /></RutaProtegida>} />
        <Route path="/huesped/busqueda/:id"
          element={<RutaProtegida rol="HUESPED"><DetalleAlojamientoPage /></RutaProtegida>} />
        <Route path="/huesped/publicaciones/:id/calendario"
          element={<RutaProtegida rol="HUESPED"><CalendarioPage /></RutaProtegida>} />
        <Route path="/huesped/reservas"
          element={<RutaProtegida rol="HUESPED"><MisReservasPage /></RutaProtegida>} />
        <Route path="/huesped/reservas/:id"
          element={<RutaProtegida rol="HUESPED"><DetalleReservaPage /></RutaProtegida>} />
        <Route path="/huesped/reservas/:id/pago"
          element={<RutaProtegida rol="HUESPED"><PortalPagoPage /></RutaProtegida>} />
        <Route path="/administrador/publicaciones"
          element={<RutaProtegida rol="ADMINISTRADOR"><RevisionPublicacionesPage /></RutaProtegida>} />
        <Route path="/administrador/moderacion"
          element={<RutaProtegida rol="ADMINISTRADOR"><ModeracionPage /></RutaProtegida>} />
        <Route path="/administrador/resenas"
          element={<RutaProtegida rol="ADMINISTRADOR"><ModeracionResenasPage /></RutaProtegida>} />
        <Route path="/administrador/usuarios"
          element={<RutaProtegida rol="ADMINISTRADOR"><AdministracionUsuariosPage /></RutaProtegida>} />
        <Route path="/administrador/auditoria"
          element={<RutaProtegida rol="ADMINISTRADOR"><AuditoriaPage /></RutaProtegida>} />
        <Route path="/administrador/auditoria/:id"
          element={<RutaProtegida rol="ADMINISTRADOR"><DetalleEventoAuditoriaPage /></RutaProtegida>} />
        {ROLES_CON_PANEL.map(({ rol, ruta }) => (
          <Route key={ruta} path={ruta} element={<RutaProtegida rol={rol}><PanelRolPage /></RutaProtegida>} />
        ))}
      </Routes>
    </ProveedorSesion>
  );
}