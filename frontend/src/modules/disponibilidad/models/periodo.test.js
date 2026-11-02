import { describe, expect, it } from 'vitest';
import { fechaLegible, periodoDesdeApi } from './periodo.js';

describe('periodo', () => {
  it('convierte la respuesta de la API en el modelo del periodo', () => {
    expect(periodoDesdeApi({ id: 5, publicacionId: 3, fechaInicio: '2027-01-10', fechaFin: '2027-01-15', otro: 1 }))
      .toEqual({ id: 5, publicacionId: 3, fechaInicio: '2027-01-10', fechaFin: '2027-01-15' });
  });

  it('muestra la fecha completa en español sin correrla un día', () => {
    expect(fechaLegible('2027-01-01')).toBe('1 de enero de 2027');
    expect(fechaLegible('2027-12-31')).toBe('31 de diciembre de 2027');
  });
});
