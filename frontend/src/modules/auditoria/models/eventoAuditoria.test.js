import { describe, expect, it } from 'vitest';
import { ACCIONES_AUDITORIA, formatearDatos, formatearFechaBogota } from './eventoAuditoria.js';

describe('ACCIONES_AUDITORIA', () => {
  it('lista los seis valores de AccionCritica para el selector', () => {
    expect(ACCIONES_AUDITORIA).toEqual([
      'AUTENTICACION', 'PUBLICACION', 'RESERVA', 'CANCELACION', 'RESENA', 'ACCION_ADMINISTRATIVA',
    ]);
  });
});

describe('formatearFechaBogota', () => {
  it('muestra el instante en hora de Bogotá (UTC-5)', () => {
    expect(formatearFechaBogota('2026-11-16T03:30:00Z')).toBe('15/11/2026 22:30:00');
  });

  it('respeta el cambio de día en la frontera de las 05:00 UTC', () => {
    expect(formatearFechaBogota('2026-11-16T05:00:00Z')).toBe('16/11/2026 00:00:00');
  });

  it('acepta el offset con fracciones de segundo', () => {
    expect(formatearFechaBogota('2026-11-16T04:59:59.999-05:00')).toBe('16/11/2026 04:59:59');
  });

  it('devuelve vacío si no hay fecha y el texto original si no es una fecha', () => {
    expect(formatearFechaBogota(null)).toBe('');
    expect(formatearFechaBogota('no es fecha')).toBe('no es fecha');
  });
});

describe('formatearDatos', () => {
  it('muestra el objeto como JSON indentado', () => {
    expect(formatearDatos({ operacion: 'LOGIN', anidado: { a: 1 } })).toBe(
      '{\n  "operacion": "LOGIN",\n  "anidado": {\n    "a": 1\n  }\n}',
    );
  });

  it('sin datos muestra un objeto vacío', () => {
    expect(formatearDatos(null)).toBe('{}');
    expect(formatearDatos(undefined)).toBe('{}');
  });
});
