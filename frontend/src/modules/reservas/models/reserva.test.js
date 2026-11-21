import { describe, expect, it } from 'vitest';
import { cancelacionDesdeApi, reservaDesdeApi } from './reserva.js';

describe('reservaDesdeApi', () => {
  it('convierte los importes, que llegan como texto, a número', () => {
    const reserva = reservaDesdeApi({
      id: 12, publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', estado: 'PENDIENTE', noches: 4,
      precioBase: '400000.00', tarifaLimpieza: '30000.00', tarifaServicio: '40000.00', descuento: '0.00',
      totalServiciosAdicionales: '15000.00', total: '485000.00', estadoPago: 'SIN_PAGO', transaccionId: null,
    });

    expect(reserva).toEqual({
      id: 12, publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', estado: 'PENDIENTE', noches: 4,
      precioBase: 400000, tarifaLimpieza: 30000, tarifaServicio: 40000, descuento: 0,
      totalServiciosAdicionales: 15000, total: 485000, estadoPago: 'SIN_PAGO', transaccionId: null,
    });
  });

  it('conserva el id, las fechas y el estado tal como llegan', () => {
    const reserva = reservaDesdeApi({
      id: 1, publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', estado: 'PENDIENTE', noches: 4,
      precioBase: 400000, tarifaLimpieza: 30000, tarifaServicio: 40000, descuento: 0,
      totalServiciosAdicionales: 0, total: 470000,
    });

    expect(reserva.id).toBe(1);
    expect(reserva.estado).toBe('PENDIENTE');
  });

  it('conserva el estado del pago y la transacción de una reserva pagada', () => {
    const reserva = reservaDesdeApi({ id: 1, estado: 'CONFIRMADA', estadoPago: 'APROBADO', transaccionId: 'pi_123' });

    expect(reserva.estadoPago).toBe('APROBADO');
    expect(reserva.transaccionId).toBe('pi_123');
  });
});

describe('cancelacionDesdeApi', () => {
  it('convierte el valor de devolución, que llega como texto, a número', () => {
    const cancelacion = cancelacionDesdeApi({
      reservaId: 7, estado: 'CANCELADA', motivo: 'Cambio de planes', valorDevolucion: '470000.00',
      fecha: '2026-12-01T15:00:00Z',
    });

    expect(cancelacion).toEqual({
      reservaId: 7, estado: 'CANCELADA', motivo: 'Cambio de planes', valorDevolucion: 470000,
      fecha: '2026-12-01T15:00:00Z',
    });
  });
});
