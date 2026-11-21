import { describe, expect, it } from 'vitest';
import { aVistaNotificacion } from './notificacion.js';

describe('aVistaNotificacion', () => {
  it('conserva los datos y muestra la fecha de creación en hora de Bogotá', () => {
    const vista = aVistaNotificacion({
      id: 1, tipo: 'RESERVA_CONFIRMADA', mensaje: 'Confirmada', entidad: 'Reserva', entidadId: 7,
      leida: false, creadaEn: '2026-11-21T10:05:00-05:00',
    });

    expect(vista).toEqual({ id: 1, tipo: 'RESERVA_CONFIRMADA', mensaje: 'Confirmada', leida: false,
      creadaEn: '2026-11-21T10:05:00-05:00', fecha: '21/11/2026 10:05' });
  });

  it('sin fecha legible deja el texto original', () => {
    expect(aVistaNotificacion({ id: 2, mensaje: 'x', leida: true, creadaEn: 'ayer' }).fecha).toBe('ayer');
  });
});
