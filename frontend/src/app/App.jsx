import { Link, Route, Routes } from 'react-router-dom';
import InicioSesionPage from '../modules/identidad/pages/InicioSesionPage.jsx';
import RegistroPage from '../modules/identidad/pages/RegistroPage.jsx';
import Encabezado from '../shared/components/Encabezado.jsx';
import ProveedorSesion from '../shared/sesion/ProveedorSesion.jsx';

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
  return (
    <ProveedorSesion>
      <Encabezado />
      <Routes>
        <Route path="/" element={<Inicio />} />
        <Route path="/registro" element={<RegistroPage />} />
        <Route path="/inicio-sesion" element={<InicioSesionPage />} />
      </Routes>
    </ProveedorSesion>
  );
}
