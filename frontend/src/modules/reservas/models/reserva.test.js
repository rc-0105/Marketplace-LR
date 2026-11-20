import { describe, expect, it } from 'vitest';
import { reservaDesdeApi } from './reserva.js';

describe('reservaDesdeApi', () => {
  it('convierte los importes, que llegan como texto, a número', () => {
    const reserva = reservaDesdeApi({
      id: 12, publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', estado: 'PENDIENTE', noches: 4,
      precioBase: '400000.00', tarifaLimpieza: '30000.00', tarifaServicio: '40000.00', descuento: '0.00',
      totalServiciosAdicionales: '15000.00', total: '485000.00',
    });

    expect(reserva).toEqual({
      id: 12, publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', estado: 'PENDIENTE', noches: 4,
      precioBase: 400000, tarifaLimpieza: 30000, tarifaServicio: 40000, descuento: 0,
      totalServiciosAdicionales: 15000, total: 485000,
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
});
