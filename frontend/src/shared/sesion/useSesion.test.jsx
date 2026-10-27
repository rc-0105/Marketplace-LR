import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { guardarSesion, limpiarSesion } from '../services/sesion.js';
import { homeDe } from './rutasPorRol.js';
import { useSesion } from './useSesion.js';

const sesion = {
  token: 'abc',
  usuario: { id: 1, rol: 'HUESPED' },
  expiraEn: new Date(Date.now() + 60_000).toISOString(),
};

describe('useSesion', () => {
  beforeEach(() => {
    limpiarSesion();
  });

  it('devuelve null cuando no hay sesión, sin necesidad de proveedor', () => {
    const { result } = renderHook(() => useSesion());

    expect(result.current).toBeNull();
  });

  it('refleja la sesión guardada y se actualiza al guardar y al limpiar', () => {
    const { result } = renderHook(() => useSesion());

    act(() => guardarSesion(sesion));
    expect(result.current).toEqual(sesion);

    act(() => limpiarSesion());
    expect(result.current).toBeNull();
  });
});

describe('homeDe', () => {
  it('lleva cada rol a su panel y usa el inicio para un rol desconocido', () => {
    expect(homeDe('HUESPED')).toBe('/huesped');
    expect(homeDe('ANFITRION')).toBe('/anfitrion');
    expect(homeDe('ADMINISTRADOR')).toBe('/administrador');
    expect(homeDe('OTRO')).toBe('/');
  });
});
