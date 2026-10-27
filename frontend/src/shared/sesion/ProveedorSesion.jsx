import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { limpiarSesion, obtenerSesion, suscribirExpiracion } from '../services/sesion.js';
import { useSesion } from './useSesion.js';

// setTimeout guarda el retardo en 32 bits con signo: un valor mayor se desborda y dispara de inmediato.
const RETARDO_MAXIMO_MS = 2 ** 31 - 1;

/**
 * Solo maneja efectos de la sesión: no expone contexto ni pinta markup.
 * Debe vivir dentro del Router.
 */
export default function ProveedorSesion({ children }) {
  const navegar = useNavigate();
  const expiraEn = useSesion()?.expiraEn;

  useEffect(
    () => suscribirExpiracion(() => navegar('/inicio-sesion', { replace: true, state: { sesionExpirada: true } })),
    [navegar],
  );

  useEffect(() => {
    if (!expiraEn) {
      return undefined;
    }
    let temporizador;
    const programar = () => {
      const restante = new Date(expiraEn) - Date.now();
      temporizador = setTimeout(() => {
        // Vencimiento más lejano que el tope del timer: se vuelve a programar lo que falta.
        if (new Date(expiraEn) > Date.now()) {
          programar();
          return;
        }
        // Si ya hay otra sesión vigente, este temporizador es de una sesión anterior.
        const actual = obtenerSesion();
        if (actual && actual.expiraEn !== expiraEn) {
          return;
        }
        limpiarSesion('expirada');
      }, Math.min(restante, RETARDO_MAXIMO_MS));
    };
    programar();
    return () => clearTimeout(temporizador);
  }, [expiraEn]);

  return children;
}
