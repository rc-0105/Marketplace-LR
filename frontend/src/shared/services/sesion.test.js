import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { guardarSesion, limpiarSesion, obtenerSesion, suscribir, suscribirExpiracion } from './sesion.js';

const CLAVE = 'marketplace.sesion';
const USUARIO = { id: 7, nombre: 'Ana', correo: 'ana@correo.com', rol: 'HUESPED', activa: true };

function sesionVigente() {
  return { token: 'abc', usuario: USUARIO, expiraEn: new Date(Date.now() + 60_000).toISOString() };
}

describe('sesion', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('guarda la sesión en sessionStorage y nunca toca localStorage', () => {
    const sesion = sesionVigente();

    guardarSesion(sesion);

    expect(JSON.parse(sessionStorage.getItem(CLAVE))).toEqual(sesion);
    expect(localStorage.length).toBe(0);
    expect(obtenerSesion()).toEqual(sesion);
  });

  it('trata una sesión vencida como si no existiera y la limpia', () => {
    guardarSesion({ ...sesionVigente(), expiraEn: new Date(Date.now() - 1000).toISOString() });

    expect(obtenerSesion()).toBeNull();
    expect(sessionStorage.getItem(CLAVE)).toBeNull();
  });

  it('invalida una sesión que vence estando en cache y mantiene el resultado estable', () => {
    vi.useFakeTimers();
    try {
      guardarSesion(sesionVigente());
      expect(obtenerSesion()).not.toBeNull();

      vi.advanceTimersByTime(61_000);

      const primera = obtenerSesion();
      expect(primera).toBeNull();
      expect(obtenerSesion()).toBe(primera);
      expect(sessionStorage.getItem(CLAVE)).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('devuelve la misma referencia mientras el contenido guardado no cambie', () => {
    guardarSesion(sesionVigente());

    expect(obtenerSesion()).toBe(obtenerSesion());
  });

  it('notifica a los suscriptores al guardar y al limpiar', () => {
    const oyente = vi.fn();
    const desuscribir = suscribir(oyente);

    guardarSesion(sesionVigente());
    limpiarSesion();
    desuscribir();
    guardarSesion(sesionVigente());

    expect(oyente).toHaveBeenCalledTimes(2);
  });

  it('avisa la expiración solo cuando se limpia con el motivo expirada', () => {
    const oyente = vi.fn();
    suscribirExpiracion(oyente);
    guardarSesion(sesionVigente());

    limpiarSesion();
    expect(oyente).not.toHaveBeenCalled();

    limpiarSesion('expirada');
    expect(oyente).toHaveBeenCalledTimes(1);
  });

  it('no falla y no hay sesión si sessionStorage no está disponible', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('bloqueado'); });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('bloqueado'); });
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('bloqueado'); });

    expect(() => guardarSesion(sesionVigente())).not.toThrow();
    expect(obtenerSesion()).toBeNull();
    expect(() => limpiarSesion()).not.toThrow();
  });
});
