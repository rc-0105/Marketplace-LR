import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import Campo from '../../../shared/components/Campo.jsx';
import { homeDe } from '../../../shared/sesion/rutasPorRol.js';
import { validarInicioSesion } from '../models/usuario.js';
import { iniciarSesion } from '../services/identidadService.js';
import estilos from './InicioSesionPage.module.css';

export default function InicioSesionPage() {
  const { state } = useLocation();
  const navegar = useNavigate();
  const [datos, setDatos] = useState({ correo: '', contrasena: '' });
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [enviando, setEnviando] = useState(false);

  function cambiar(evento) {
    const { name, value } = evento.target;
    setDatos({ ...datos, [name]: value });
  }

  async function enviar(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    const erroresFormato = validarInicioSesion(datos);
    setErrores(erroresFormato);
    if (Object.keys(erroresFormato).length > 0) {
      return;
    }

    setEnviando(true);
    try {
      const usuario = await iniciarSesion({ correo: datos.correo.trim(), contrasena: datos.contrasena });
      navegar(homeDe(usuario.rol), { replace: true });
    } catch (error) {
      setEnviando(false);
      setErrorGeneral(error.message);
    }
  }

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Inicia sesión</h1>
      {state?.registrado && (
        <p role="status" className={estilos.exito}>Tu cuenta quedó creada. Ya puedes iniciar sesión.</p>
      )}
      {state?.sesionExpirada && (
        <p role="status" className={estilos.exito}>Tu sesión venció. Inicia sesión de nuevo para continuar.</p>
      )}

      {errorGeneral && <p role="alert" className={estilos.aviso}>{errorGeneral}</p>}

      <form className={estilos.formulario} onSubmit={enviar} noValidate>
        <Campo id="correo" etiqueta="Correo electrónico" type="email" autoComplete="email"
          value={datos.correo} onChange={cambiar} error={errores.correo} />
        <Campo id="contrasena" etiqueta="Contraseña" type="password" autoComplete="current-password"
          value={datos.contrasena} onChange={cambiar} error={errores.contrasena} />
        <button type="submit" className={estilos.boton} disabled={enviando}>
          {enviando ? 'Iniciando sesión…' : 'Iniciar sesión'}
        </button>
      </form>

      <p className={estilos.pie}>¿Aún no tienes cuenta? <Link to="/registro">Crea una</Link></p>
    </main>
  );
}
