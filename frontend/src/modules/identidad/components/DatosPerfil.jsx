import { useState } from 'react';
import PropTypes from 'prop-types';
import Campo from '../../../shared/components/Campo.jsx';
import { validarPerfil } from '../models/usuario.js';
import estilos from './DatosPerfil.module.css';

/**
 * Formulario de los datos básicos del perfil. El correo se muestra pero no se edita.
 * `alGuardar` llama a la API: devuelve el perfil guardado o lanza el ErrorApi.
 */
export default function DatosPerfil({ perfil, alGuardar }) {
  const [datos, setDatos] = useState({ nombre: perfil.nombre, telefono: perfil.telefono, fotoUrl: perfil.fotoUrl });
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [guardado, setGuardado] = useState(false);
  const [enviando, setEnviando] = useState(false);

  function cambiar(evento) {
    const { name, value } = evento.target;
    setDatos({ ...datos, [name]: value });
    setGuardado(false);
  }

  async function guardar(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    setGuardado(false);
    const erroresFormato = validarPerfil(datos);
    setErrores(erroresFormato);
    if (Object.keys(erroresFormato).length > 0) {
      return;
    }

    setEnviando(true);
    try {
      const actualizado = await alGuardar({
        nombre: datos.nombre.trim(), telefono: datos.telefono.trim(), fotoUrl: datos.fotoUrl.trim(),
      });
      setDatos({ nombre: actualizado.nombre, telefono: actualizado.telefono, fotoUrl: actualizado.fotoUrl });
      setGuardado(true);
    } catch (error) {
      setErrores(error.errores ?? {});
      setErrorGeneral(error.message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className={estilos.formulario} onSubmit={guardar} noValidate>
      {guardado && <output className={estilos.exito}>Tus datos quedaron guardados.</output>}
      {errorGeneral && <p role="alert" className={estilos.aviso}>{errorGeneral}</p>}
      <div className={estilos.correo}>
        <span className={estilos.etiqueta}>Correo electrónico</span>
        <span>{perfil.correo}</span>
        <span className={estilos.ayuda}>El correo no se puede cambiar.</span>
      </div>
      <Campo id="nombre" etiqueta="Nombre" autoComplete="name" maxLength={100}
        value={datos.nombre} onChange={cambiar} error={errores.nombre} />
      <Campo id="telefono" etiqueta="Teléfono" type="tel" autoComplete="tel" inputMode="tel" maxLength={16}
        value={datos.telefono} onChange={cambiar} error={errores.telefono} ayuda="Opcional. Ejemplo: +573001234567" />
      <Campo id="fotoUrl" etiqueta="Foto de perfil (dirección web)" type="url" inputMode="url" maxLength={500}
        value={datos.fotoUrl} onChange={cambiar} error={errores.fotoUrl}
        ayuda="Opcional. Pega el enlace de una imagen que empiece con https://" />
      <button type="submit" className={estilos.boton} disabled={enviando}>
        {enviando ? 'Guardando…' : 'Guardar cambios'}
      </button>
    </form>
  );
}

DatosPerfil.propTypes = {
  perfil: PropTypes.shape({
    nombre: PropTypes.string.isRequired,
    correo: PropTypes.string.isRequired,
    telefono: PropTypes.string.isRequired,
    fotoUrl: PropTypes.string.isRequired,
  }).isRequired,
  alGuardar: PropTypes.func.isRequired,
};
