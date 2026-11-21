import { describe, expect, it } from 'vitest';
import { fechaLegible, momentoDe, nochesDe, periodoDesdeApi } from './periodo.js';

describe('periodo', () => {
  it('convierte la respuesta de la API en el modelo del periodo', () => {
    expect(periodoDesdeApi({ id: 5, publicacionId: 3, fechaInicio: '2027-01-10', fechaFin: '2027-01-15', otro: 1 }))
      .toEqual({ id: 5, publicacionId: 3, fechaInicio: '2027-01-10', fechaFin: '2027-01-15' });
  });

  it('muestra la fecha completa en español sin correrla un día', () => {
    expect(fechaLegible('2027-01-01')).toBe('1 de enero de 2027');
    expect(fechaLegible('2027-12-31')).toBe('31 de diciembre de 2027');
  });

  it('cuenta las noches sin contar el día de salida', () => {
    expect(nochesDe({ fechaInicio: '2027-01-10', fechaFin: '2027-01-15' })).toBe(5);
    expect(nochesDe({ fechaInicio: '2027-02-27', fechaFin: '2027-03-02' })).toBe(3);
  });

  it.each([
    ['2027-01-10', '2027-01-15', '2027-01-09', 'PROXIMO'],
    ['2027-01-10', '2027-01-15', '2027-01-10', 'EN_CURSO'],
    ['2027-01-10', '2027-01-15', '2027-01-14', 'EN_CURSO'],
    ['2027-01-10', '2027-01-15', '2027-01-15', 'PASADO'],
  ])('ubica el periodo %s al %s respecto de hoy (%s) como %s', (fechaInicio, fechaFin, hoy, esperado) => {
    expect(momentoDe({ fechaInicio, fechaFin }, hoy)).toBe(esperado);
  });
});
