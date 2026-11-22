import { describe, expect, it } from 'vitest';
import { aVistaNotificacion, enlaceDeNotificacion } from './notificacion.js';

describe('enlaceDeNotificacion', () => {
  it('SOLICITUD_RECIBIDA lleva al anfitrión a sus solicitudes', () => {
    expect(enlaceDeNotificacion({ tipo: 'SOLICITUD_RECIBIDA', entidadId: 9 }))
      .toEqual({ ruta: '/anfitrion/solicitudes', texto: 'Ver solicitudes' });
  });

  it.each(['SOLICITUD_APROBADA', 'SOLICITUD_RECHAZADA', 'SOLICITUD_VENCIDA'])(
    '%s lleva al huésped al detalle de su reserva', (tipo) => {
      expect(enlaceDeNotificacion({ tipo, entidadId: 9 }))
        .toEqual({ ruta: '/huesped/reservas/9', texto: 'Ver reserva' });
    });

  it('otros tipos o una solicitud sin entidad no tienen enlace', () => {
    expect(enlaceDeNotificacion({ tipo: 'RESERVA_CONFIRMADA', entidadId: 9 })).toBeNull();
    expect(enlaceDeNotificacion({ tipo: 'SOLICITUD_APROBADA', entidadId: null })).toBeNull();
  });
});

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
