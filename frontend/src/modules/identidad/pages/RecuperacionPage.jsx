import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Campo from '../../../shared/components/Campo.jsx';
import RequisitosContrasena from '../components/RequisitosContrasena.jsx';
import { validarConfirmacionRecuperacion, validarSolicitudRecuperacion } from '../models/usuario.js';
import { confirmarRecuperacion, solicitarRecuperacion } from '../services/identidadService.js';
import estilos from './RecuperacionPage.module.css';

/** Dos pasos: pedir el código con el correo y luego definir la contraseña nueva con él. */
export default function RecuperacionPage() {
  const navegar = useNavigate();
  const [datos, setDatos] = useState({ correo: '', codigo: '', contrasenaNueva: '' });
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [mensajeSolicitud, setMensajeSolicitud] = useState('');
  const [enviando, setEnviando] = useState(false);

  function cambiar(evento) {
    const { name, value } = evento.target;
    setDatos({ ...datos, [name]: value });
  }

  async function solicitar(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    const erroresFormato = validarSolicitudRecuperacion(datos);
    setErrores(erroresFormato);
    if (Object.keys(erroresFormato).length > 0) {
      return;
    }

    setEnviando(true);
    try {
      setMensajeSolicitud(await solicitarRecuperacion(datos.correo.trim()));
    } catch (error) {
      setErrorGeneral(error.message);
    } finally {
      setEnviando(false);
    }
  }

  async function confirmar(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    const erroresFormato = validarConfirmacionRecuperacion(datos);
    setErrores(erroresFormato);
    if (Object.keys(erroresFormato).length > 0) {
      return;
    }

    setEnviando(true);
    try {
      await confirmarRecuperacion({
        correo: datos.correo.trim(), codigo: datos.codigo.trim(), contrasenaNueva: datos.contrasenaNueva,
      });
      navegar('/inicio-sesion', { replace: true, state: { contrasenaRestablecida: true } });
    } catch (error) {
      setEnviando(false);
      setErrores(error.errores ?? {});
      setErrorGeneral(error.message);
    }
  }

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Recupera tu contraseña</h1>

      {mensajeSolicitud ? (
        <>
          <output className={estilos.exito}>{mensajeSolicitud}</output>
          {errorGeneral && <p role="alert" className={estilos.aviso}>{errorGeneral}</p>}
          <form className={estilos.formulario} onSubmit={confirmar} noValidate>
            <Campo id="codigo" etiqueta="Código de recuperación" inputMode="numeric" autoComplete="one-time-code"
              maxLength={6} value={datos.codigo} onChange={cambiar} error={errores.codigo}
              ayuda="Revisa tu bandeja de entrada y la carpeta de spam. El código solo sirve una vez." />
            <div>
              <Campo id="contrasenaNueva" etiqueta="Contraseña nueva" type="password" autoComplete="new-password"
                value={datos.contrasenaNueva} onChange={cambiar} error={errores.contrasenaNueva} />
              <RequisitosContrasena contrasena={datos.contrasenaNueva} />
            </div>
            <button type="submit" className={estilos.boton} disabled={enviando}>
              {enviando ? 'Guardando…' : 'Guardar contraseña'}
            </button>
          </form>
        </>
      ) : (
        <>
          <p className={estilos.texto}>Escribe el correo de tu cuenta y te enviaremos un código de un solo uso.</p>
          {errorGeneral && <p role="alert" className={estilos.aviso}>{errorGeneral}</p>}
          <form className={estilos.formulario} onSubmit={solicitar} noValidate>
            <Campo id="correo" etiqueta="Correo electrónico" type="email" autoComplete="email"
              value={datos.correo} onChange={cambiar} error={errores.correo} />
            <button type="submit" className={estilos.boton} disabled={enviando}>
              {enviando ? 'Enviando…' : 'Enviar código'}
            </button>
          </form>
        </>
      )}

      <p className={estilos.pie}><Link to="/inicio-sesion">Volver a iniciar sesión</Link></p>
    </main>
  );
}
