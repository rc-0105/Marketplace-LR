import { describe, expect, it } from 'vitest';
import { nombreDeEstadoReserva, nombreDeRol, sumar } from './dashboard.js';

describe('modelo del dashboard', () => {
  it('traduce roles y estados, y deja tal cual los desconocidos', () => {
    expect(nombreDeRol('ANFITRION')).toBe('Anfitriones');
    expect(nombreDeRol('OTRO')).toBe('OTRO');
    expect(nombreDeEstadoReserva('VENCIDA')).toBe('Vencidas');
    expect(nombreDeEstadoReserva('EN_REVISION_MANUAL')).toBe('En revisión manual');
    expect(nombreDeEstadoReserva('OTRO')).toBe('OTRO');
  });

  it('suma los conteos', () => {
    expect(sumar({ A: 2, B: 3 })).toBe(5);
    expect(sumar({})).toBe(0);
  });
});
