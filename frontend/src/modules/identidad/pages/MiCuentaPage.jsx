import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import CambioContrasena from '../components/CambioContrasena.jsx';
import ConfirmacionCuenta from '../components/ConfirmacionCuenta.jsx';
import DatosPerfil from '../components/DatosPerfil.jsx';
import {
  actualizarPerfil, cambiarContrasena, desactivarCuenta, eliminarCuenta, obtenerPerfil,
} from '../services/identidadService.js';
import estilos from './MiCuentaPage.module.css';

const MENSAJE_CONTRASENA = 'La contraseña no es correcta';
const MENSAJE_GENERICO = 'No pudimos completar la operación. Intenta de nuevo.';

const OPERACIONES = {
  desactivar: {
    titulo: 'Desactivar mi cuenta',
    resumen: 'Tu cuenta quedará inactiva y cerraremos tu sesión.',
    tituloConfirmacion: 'Confirma la desactivación',
    advertencia: 'Podrás reactivarla cuando quieras desde el inicio de sesión.',
    textoConfirmar: 'Sí, desactivar mi cuenta',
    ejecutar: desactivarCuenta,
  },
  eliminar: {
    titulo: 'Eliminar mi cuenta',
    resumen: 'Tu cuenta se elimina de forma definitiva.',
    tituloConfirmacion: 'Confirma la eliminación',
    advertencia: 'Esta acción no se puede deshacer. Se borrarán tu nombre y tu correo y no podrás volver a entrar.',
    textoConfirmar: 'Sí, eliminar mi cuenta',
    ejecutar: eliminarCuenta,
  },
};

// Solo se ramifica por el código de negocio; el texto del detalle nunca decide nada.
function mensajeDe(error) {
  if (error.codigo === 'CONFIRMACION_INVALIDA') {
    return MENSAJE_CONTRASENA;
  }
  if (error.codigo === 'RESERVAS_FUTURAS_ACTIVAS') {
    return error.message;
  }
  return MENSAJE_GENERICO;
}

export default function MiCuentaPage() {
  const navegar = useNavigate();
  const [operacion, setOperacion] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState(null);
  const [perfil, setPerfil] = useState(null);
  const [errorPerfil, setErrorPerfil] = useState('');

  useEffect(() => {
    let vigente = true;
    obtenerPerfil()
      .then((datos) => vigente && setPerfil(datos))
      .catch((error_) => vigente && setErrorPerfil(error_.message));
    return () => {
      vigente = false;
    };
  }, []);

  function abrir(clave) {
    setOperacion(clave);
    setError(null);
  }

  async function confirmar(contrasena) {
    setEnviando(true);
    setError(null);
    try {
      await OPERACIONES[operacion].ejecutar(contrasena);
      navegar('/inicio-sesion', { replace: true });
    } catch (error_) {
      setError(mensajeDe(error_));
      setEnviando(false);
    }
  }

  async function guardarPerfil(datos) {
    const actualizado = await actualizarPerfil(datos);
    setPerfil(actualizado);
    return actualizado;
  }

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Mi cuenta</h1>
      <section className={estilos.seccion} aria-labelledby="seccion-datos">
        <h2 id="seccion-datos" className={estilos.subtitulo}>Mis datos</h2>
        {perfil?.fotoUrl && <img className={estilos.foto} src={perfil.fotoUrl} alt="Tu foto de perfil" />}
        {errorPerfil && <p role="alert" className={estilos.aviso}>{errorPerfil}</p>}
        {!perfil && !errorPerfil && <p className={estilos.resumen}>Cargando tus datos…</p>}
        {perfil && <DatosPerfil perfil={perfil} alGuardar={guardarPerfil} />}
      </section>
      <section className={estilos.seccion} aria-labelledby="seccion-contrasena">
        <h2 id="seccion-contrasena" className={estilos.subtitulo}>Cambiar contraseña</h2>
        <CambioContrasena alCambiar={cambiarContrasena} />
      </section>
      {Object.entries(OPERACIONES).map(([clave, datos]) => (
        <section key={clave} className={estilos.seccion} aria-labelledby={`seccion-${clave}`}>
          <h2 id={`seccion-${clave}`} className={estilos.subtitulo}>{datos.titulo}</h2>
          <p className={estilos.resumen}>{datos.resumen}</p>
          <button type="button" className={estilos.boton} disabled={enviando} onClick={() => abrir(clave)}>
            {datos.titulo}
          </button>
          {operacion === clave && (
            <ConfirmacionCuenta titulo={datos.tituloConfirmacion} advertencia={datos.advertencia}
              textoConfirmar={datos.textoConfirmar} enviando={enviando} error={error ?? undefined}
              alConfirmar={confirmar} alCancelar={() => abrir(null)} />
          )}
        </section>
      ))}
    </main>
  );
}
