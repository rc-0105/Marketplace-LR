import { useCallback, useEffect, useRef, useState } from 'react';
import Icono from '../../../shared/components/Icono.jsx';
import { useSesion } from '../../../shared/sesion/useSesion.js';
import { errorDeMotivo } from '../models/usuario.js';
import {
  listarUsuariosAdministrables, rehabilitarUsuario, restringirUsuario,
} from '../services/identidadService.js';
import estilos from './AdministracionUsuariosPage.module.css';

const MENSAJE_ERROR = 'No pudimos cargar los usuarios. Intenta de nuevo.';
const MENSAJE_ACCION_FALLIDA = 'No pudimos guardar el cambio. Intenta de nuevo.';
const NOMBRE_ROL = { HUESPED: 'Huésped', ANFITRION: 'Anfitrión', ADMINISTRADOR: 'Administrador' };
const NOMBRE_ESTADO = { ACTIVA: 'Activa', DESACTIVADA: 'Desactivada', RESTRINGIDA: 'Restringida' };

/** Pantalla del administrador para buscar cuentas, restringirlas con un motivo y rehabilitarlas (RF-58). */
export default function AdministracionUsuariosPage() {
  const sesion = useSesion();
  const [texto, setTexto] = useState('');
  const [buscado, setBuscado] = useState('');
  const [usuarios, setUsuarios] = useState(null);
  const [errorCarga, setErrorCarga] = useState(null);
  const [motivos, setMotivos] = useState({});
  const [erroresMotivo, setErroresMotivo] = useState({});
  const [errores, setErrores] = useState({});
  const [enCurso, setEnCurso] = useState(null);
  const [confirmacion, setConfirmacion] = useState(null);

  // Cada carga lleva un número: solo la última aplica su respuesta, y ninguna tras desmontar la página.
  const ultimaCarga = useRef(0);

  const cargar = useCallback(async (filtro) => {
    const numero = ++ultimaCarga.current;
    setErrorCarga(null);
    try {
      const lista = await listarUsuariosAdministrables(filtro);
      if (numero === ultimaCarga.current) {
        setUsuarios(lista);
      }
    } catch {
      if (numero === ultimaCarga.current) {
        setErrorCarga(MENSAJE_ERROR);
      }
    }
  }, []);

  useEffect(() => {
    cargar('');
    return () => {
      ultimaCarga.current += 1;
    };
  }, [cargar]);

  async function buscar(evento) {
    evento.preventDefault();
    setConfirmacion(null);
    setBuscado(texto);
    setUsuarios(null);
    await cargar(texto);
  }

  // La API decide si el cambio procede; si lo acepta, se vuelve a pedir la lista para mostrar su estado.
  async function ejecutar(usuario, accion, mensaje) {
    setEnCurso(usuario.id);
    setConfirmacion(null);
    setErrores((actuales) => ({ ...actuales, [usuario.id]: null }));
    try {
      await accion();
      setMotivos((actuales) => ({ ...actuales, [usuario.id]: '' }));
      setConfirmacion(mensaje);
      await cargar(buscado);
    } catch (error) {
      setErrores((actuales) => ({
        ...actuales,
        [usuario.id]: error.estado ? error.message : MENSAJE_ACCION_FALLIDA,
      }));
    } finally {
      setEnCurso(null);
    }
  }

  async function restringir(evento, usuario) {
    evento.preventDefault();
    const motivo = motivos[usuario.id] ?? '';
    const error = errorDeMotivo(motivo);
    setErroresMotivo((actuales) => ({ ...actuales, [usuario.id]: error }));
    if (error) {
      return;
    }
    await ejecutar(usuario, () => restringirUsuario(usuario.id, motivo), `La cuenta de ${usuario.nombre} quedó restringida.`);
  }

  async function rehabilitar(usuario) {
    await ejecutar(usuario, () => rehabilitarUsuario(usuario.id), `La cuenta de ${usuario.nombre} recuperó el acceso.`);
  }

  return (
    <main className={estilos.pagina}>
      <header className={estilos.cabeceraPagina}>
        <div>
          <h1 className={estilos.titulo}>Usuarios</h1>
          <p className={estilos.nota}>
            Restringe las cuentas que incumplen las políticas: no podrán iniciar sesión y sus publicaciones
            dejarán de aparecer en las búsquedas hasta que las rehabilites.
          </p>
        </div>
        {usuarios?.length > 0 && (
          <span className={estilos.contador}>{usuarios.length === 1 ? '1 usuario' : `${usuarios.length} usuarios`}</span>
        )}
      </header>
      <form className={estilos.buscador} role="search" onSubmit={buscar}>
        <label className={estilos.etiqueta} htmlFor="texto-usuarios">Buscar por nombre o correo</label>
        <div className={estilos.filaBuscador}>
          <input id="texto-usuarios" type="search" className={estilos.control} value={texto}
            onChange={(evento) => setTexto(evento.target.value)} />
          <button type="submit" className={estilos.primario}>
            <Icono nombre="buscar" />
            Buscar
          </button>
        </div>
      </form>
      <output className={confirmacion ? estilos.confirmacion : undefined}>{confirmacion}</output>
      {errorCarga && <p role="alert" className={estilos.error}>{errorCarga}</p>}
      {!errorCarga && usuarios === null && <p className={estilos.nota}>Cargando usuarios…</p>}
      {usuarios?.length === 0 && (
        <div className={estilos.vacio}>
          <span className={estilos.vacioIcono}><Icono nombre="usuarios" /></span>
          <p className={estilos.vacioTexto}>No encontramos usuarios con ese nombre o correo.</p>
          <p className={estilos.nota}>Prueba con otra parte del nombre o del correo.</p>
        </div>
      )}
      {usuarios?.length > 0 && (
        <ul className={estilos.lista}>
          {usuarios.map((usuario) => {
            const idMotivo = `motivo-${usuario.id}`;
            const errorMotivo = erroresMotivo[usuario.id];
            const ocupado = enCurso === usuario.id;
            // La API rechaza igual la propia cuenta; aquí solo se evita ofrecer una acción que no procede.
            const esPropia = usuario.id === sesion?.usuario.id;
            return (
              <li key={usuario.id} className={estilos.tarjeta} data-estado={usuario.estado}>
                <div className={estilos.cabecera}>
                  <span className={estilos.avatar} aria-hidden="true">
                    {usuario.nombre.trim().charAt(0).toUpperCase()}
                  </span>
                  <div className={estilos.datosUsuario}>
                    <h2 className={estilos.nombre}>{usuario.nombre}</h2>
                    <p className={estilos.detalle}>{usuario.correo} · {NOMBRE_ROL[usuario.rol] ?? usuario.rol}</p>
                  </div>
                  <span className={usuario.estado === 'RESTRINGIDA' ? estilos.estadoRestringida : estilos.estado}>
                    {NOMBRE_ESTADO[usuario.estado] ?? usuario.estado}
                  </span>
                </div>
                {usuario.motivoRestriccion && (
                  <p className={estilos.motivo}>Motivo: {usuario.motivoRestriccion}</p>
                )}
                {errores[usuario.id] && <p role="alert" className={estilos.error}>{errores[usuario.id]}</p>}
                {esPropia && <p className={estilos.propia}>Es tu cuenta: no puedes restringirla.</p>}
                {!esPropia && usuario.estado === 'ACTIVA' && (
                  <form className={estilos.accion} onSubmit={(evento) => restringir(evento, usuario)} noValidate>
                    <div className={estilos.campo}>
                      <label className={estilos.etiqueta} htmlFor={idMotivo}>
                        Motivo de la restricción para {usuario.nombre}
                      </label>
                      <textarea id={idMotivo} className={estilos.control} rows={2} maxLength={500}
                        placeholder="Explica por qué se restringe la cuenta."
                        value={motivos[usuario.id] ?? ''}
                        aria-invalid={errorMotivo ? true : undefined}
                        aria-describedby={errorMotivo ? `${idMotivo}-error` : undefined}
                        onChange={(evento) => setMotivos((actuales) => ({
                          ...actuales, [usuario.id]: evento.target.value,
                        }))} />
                      {errorMotivo && <p id={`${idMotivo}-error`} className={estilos.errorCampo}>{errorMotivo}</p>}
                    </div>
                    <button type="submit" className={estilos.secundario} disabled={ocupado}
                      aria-label={`Restringir a ${usuario.nombre}`}>
                      <Icono nombre="bloquear" />
                      Restringir
                    </button>
                  </form>
                )}
                {!esPropia && usuario.estado === 'RESTRINGIDA' && (
                  <div className={estilos.accion}>
                    <button type="button" className={estilos.rehabilitar} disabled={ocupado}
                      aria-label={`Rehabilitar a ${usuario.nombre}`} onClick={() => rehabilitar(usuario)}>
                      <Icono nombre="aprobar" />
                      Rehabilitar
                    </button>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}
