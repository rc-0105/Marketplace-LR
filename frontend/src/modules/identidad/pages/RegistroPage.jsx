import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Campo from '../../../shared/components/Campo.jsx';
import { AYUDA_CONTRASENA, validarRegistro } from '../models/usuario.js';
import { registrarUsuario } from '../services/identidadService.js';
import estilos from './RegistroPage.module.css';

const ROLES = [
  { valor: 'HUESPED', nombre: 'Huésped', descripcion: 'Busco y reservo alojamientos.' },
  { valor: 'ANFITRION', nombre: 'Anfitrión', descripcion: 'Publico mis alojamientos.' },
];

const INICIAL = { nombre: '', correo: '', contrasena: '', rol: 'HUESPED' };

export default function RegistroPage() {
  const navegar = useNavigate();
  const [datos, setDatos] = useState(INICIAL);
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [enviando, setEnviando] = useState(false);

  function cambiar(evento) {
    const { name, value } = evento.target;
    setDatos((previos) => ({ ...previos, [name]: value }));
  }

  async function enviar(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    const erroresFormato = validarRegistro(datos);
    setErrores(erroresFormato);
    if (Object.keys(erroresFormato).length > 0) {
      return;
    }

    setEnviando(true);
    try {
      await registrarUsuario({ ...datos, nombre: datos.nombre.trim(), correo: datos.correo.trim() });
      navegar('/inicio-sesion', { state: { registrado: true } });
    } catch (error) {
      setEnviando(false);
      if (error.estado === 409) {
        setErrores({ correo: error.message });
      } else if (Object.keys(error.errores ?? {}).length > 0) {
        setErrores(error.errores);
      } else {
        setErrorGeneral(error.message);
      }
    }
  }

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Crea tu cuenta</h1>
      <p className={estilos.intro}>Elige cómo vas a usar la plataforma. Podrás iniciar sesión apenas termines.</p>

      {errorGeneral && <p role="alert" className={estilos.aviso}>{errorGeneral}</p>}

      <form className={estilos.formulario} onSubmit={enviar} noValidate>
        <Campo id="nombre" etiqueta="Nombre completo" autoComplete="name"
          value={datos.nombre} onChange={cambiar} error={errores.nombre} />
        <Campo id="correo" etiqueta="Correo electrónico" type="email" autoComplete="email"
          value={datos.correo} onChange={cambiar} error={errores.correo} />
        <Campo id="contrasena" etiqueta="Contraseña" type="password" autoComplete="new-password"
          value={datos.contrasena} onChange={cambiar} ayuda={AYUDA_CONTRASENA} error={errores.contrasena} />

        <fieldset className={estilos.roles}>
          <legend className={estilos.leyenda}>¿Cómo vas a usar la plataforma?</legend>
          {ROLES.map((rol) => (
            <label key={rol.valor} className={estilos.rol}>
              <input type="radio" name="rol" value={rol.valor}
                checked={datos.rol === rol.valor} onChange={cambiar} />
              <span>
                <span className={estilos.rolNombre}>{rol.nombre}</span>
                <span className={estilos.rolDescripcion}>{rol.descripcion}</span>
              </span>
            </label>
          ))}
        </fieldset>

        <button type="submit" className={estilos.boton} disabled={enviando}>
          {enviando ? 'Creando cuenta…' : 'Crear cuenta'}
        </button>
      </form>

      <p className={estilos.pie}>¿Ya tienes cuenta? <Link to="/inicio-sesion">Inicia sesión</Link></p>
    </main>
  );
}
