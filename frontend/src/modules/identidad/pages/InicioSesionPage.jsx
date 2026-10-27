import { useLocation } from 'react-router-dom';
import estilos from './InicioSesionPage.module.css';

// Destino del registro. El formulario de inicio de sesión llega con SOF-12.
export default function InicioSesionPage() {
  const { state } = useLocation();

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Inicia sesión</h1>
      {state?.registrado && (
        <p role="status" className={estilos.exito}>Tu cuenta quedó creada. Ya puedes iniciar sesión.</p>
      )}
    </main>
  );
}
