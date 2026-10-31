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

// Una sección a la vez para que la página no necesite scroll.
const PESTANAS = [
  { clave: 'datos', titulo: 'Mis datos' },
  { clave: 'contrasena', titulo: 'Contraseña' },
  { clave: 'baja', titulo: 'Desactivar o eliminar' },
];

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
  const [pestana, setPestana] = useState(PESTANAS[0].clave);

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

  function elegirPestana(clave) {
    setPestana(clave);
    abrir(null);
  }

  // Patrón de pestañas WAI-ARIA: las flechas mueven la selección y el foco.
  function moverConTeclado(evento, indice) {
    const paso = { ArrowRight: 1, ArrowLeft: -1 }[evento.key];
    if (!paso) {
      return;
    }
    const siguiente = PESTANAS[(indice + paso + PESTANAS.length) % PESTANAS.length].clave;
    elegirPestana(siguiente);
    document.getElementById(`pestana-${siguiente}`)?.focus();
  }

  async function guardarPerfil(datos) {
    const actualizado = await actualizarPerfil(datos);
    setPerfil(actualizado);
    return actualizado;
  }

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Mi cuenta</h1>
      <div role="tablist" aria-label="Secciones de mi cuenta" className={estilos.pestanas}>
        {PESTANAS.map(({ clave, titulo }, indice) => (
          <button key={clave} id={`pestana-${clave}`} type="button" role="tab" className={estilos.pestana}
            aria-selected={pestana === clave} aria-controls={`panel-${clave}`} tabIndex={pestana === clave ? 0 : -1}
            onClick={() => elegirPestana(clave)} onKeyDown={(evento) => moverConTeclado(evento, indice)}>
            {titulo}
          </button>
        ))}
      </div>
      <div id={`panel-${pestana}`} role="tabpanel" aria-labelledby={`pestana-${pestana}`} className={estilos.panel}>
        {pestana === 'datos' && (
          <>
            {perfil?.fotoUrl && <img className={estilos.foto} src={perfil.fotoUrl} alt="Tu foto de perfil" />}
            {errorPerfil && <p role="alert" className={estilos.aviso}>{errorPerfil}</p>}
            {!perfil && !errorPerfil && <p className={estilos.resumen}>Cargando tus datos…</p>}
            {perfil && <DatosPerfil perfil={perfil} alGuardar={guardarPerfil} />}
          </>
        )}
        {pestana === 'contrasena' && <CambioContrasena alCambiar={cambiarContrasena} />}
        {pestana === 'baja' && Object.entries(OPERACIONES).map(([clave, datos]) => (
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
      </div>
    </main>
  );
}
