import { beforeEach, describe, expect, it, vi } from 'vitest';
import { guardarUltimaBusqueda, leerUltimaBusqueda } from './ultimaBusqueda.js';

const BUSQUEDA = {
  ciudad: 'Bogotá', llegada: '2026-11-21', salida: '2026-11-25', capacidad: '2', tipo: 'Casa',
  precioMin: '100000', precioMax: '500000', servicios: [10, 20],
};

describe('ultimaBusqueda', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('devuelve lo mismo que se guardó', () => {
    guardarUltimaBusqueda(BUSQUEDA);

    expect(leerUltimaBusqueda()).toEqual(BUSQUEDA);
  });

  it('devuelve null si no hay nada guardado', () => {
    expect(leerUltimaBusqueda()).toBeNull();
  });

  it.each([['no es JSON', 'no-json'], ['es una lista', '[1,2]'], ['es un número', '7'], ['es null', 'null']])(
    'devuelve null si lo guardado %s', (_caso, crudo) => {
      sessionStorage.setItem('marketplace.ultimaBusqueda', crudo);

      expect(leerUltimaBusqueda()).toBeNull();
    },
  );

  it('descarta los campos desconocidos o de otro tipo', () => {
    sessionStorage.setItem('marketplace.ultimaBusqueda', JSON.stringify({
      ciudad: 'Bogotá', llegada: 20261121, otro: 'x', servicios: [10, 'a', null, 20.5],
    }));

    expect(leerUltimaBusqueda()).toEqual({ ciudad: 'Bogotá', servicios: [10] });
  });

  it('no falla si el navegador no deja usar el almacenamiento', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('lleno'); });
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('bloqueado'); });

    expect(() => guardarUltimaBusqueda(BUSQUEDA)).not.toThrow();
    expect(leerUltimaBusqueda()).toBeNull();
  });
});
