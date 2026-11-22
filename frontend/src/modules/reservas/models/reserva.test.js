import { describe, expect, it } from 'vitest';
import {
  cancelacionDesdeApi, detalleReservaDesdeApi, formatearPlazo, nombreDeEstadoPago, nombreDeEstadoReserva, reservaDesdeApi,
  resumenReservaDesdeApi,
} from './reserva.js';

describe('resumenReservaDesdeApi', () => {
  it('convierte el total a número y conserva el alojamiento, las fechas y el estado', () => {
    const resumen = resumenReservaDesdeApi({
      id: 3, publicacionId: 5, titulo: 'Casa en la playa', ciudad: 'Santa Marta', imagenPrincipal: null,
      llegada: '2026-12-10', salida: '2026-12-14', noches: 4, estado: 'CONFIRMADA', total: '485000.00',
    });

    expect(resumen).toEqual({
      id: 3, publicacionId: 5, titulo: 'Casa en la playa', ciudad: 'Santa Marta', imagenPrincipal: null,
      llegada: '2026-12-10', salida: '2026-12-14', noches: 4, estado: 'CONFIRMADA', total: 485000,
    });
  });
});

describe('detalleReservaDesdeApi', () => {
  it('convierte los importes del desglose y conserva el alojamiento y los servicios adicionales', () => {
    const detalle = detalleReservaDesdeApi({
      id: 3, alojamiento: { id: 5, titulo: 'Casa en la playa', ciudad: null, imagenPrincipal: null },
      llegada: '2026-12-10', salida: '2026-12-14', estado: 'CONFIRMADA', venceEn: '2026-11-20T10:00:00Z',
      noches: 4, precioBase: '400000.00', tarifaLimpieza: '30000.00', tarifaServicio: '40000.00', descuento: '0.00',
      serviciosAdicionales: [{ id: 1, nombre: 'Desayuno' }, { id: 2, nombre: null }],
      totalServiciosAdicionales: '15000.00', total: '485000.00', estadoPago: 'APROBADO', transaccionId: 'sim_1',
      motivoRechazo: 'Fechas no disponibles',
    });

    expect(detalle).toEqual({
      id: 3, alojamiento: { id: 5, titulo: 'Casa en la playa', ciudad: null, imagenPrincipal: null },
      llegada: '2026-12-10', salida: '2026-12-14', estado: 'CONFIRMADA', venceEn: '2026-11-20T10:00:00Z',
      noches: 4, precioBase: 400000, tarifaLimpieza: 30000, tarifaServicio: 40000, descuento: 0,
      serviciosAdicionales: [{ id: 1, nombre: 'Desayuno' }, { id: 2, nombre: null }],
      totalServiciosAdicionales: 15000, total: 485000, estadoPago: 'APROBADO', transaccionId: 'sim_1',
      motivoRechazo: 'Fechas no disponibles',
    });
  });

  it('sin alojamiento ni servicios deja el alojamiento en null y la lista vacía', () => {
    const detalle = detalleReservaDesdeApi({ id: 3, alojamiento: null, estado: 'VENCIDA', estadoPago: 'SIN_PAGO' });

    expect(detalle.alojamiento).toBeNull();
    expect(detalle.serviciosAdicionales).toEqual([]);
    expect(detalle.transaccionId).toBeNull();
    expect(detalle.motivoRechazo).toBeNull();
  });
});

describe('formatearPlazo', () => {
  it('muestra el instante límite en hora de Bogotá, con día y hora', () => {
    expect(formatearPlazo('2026-11-23T15:00:00Z')).toMatch(/23.*nov.*10:00/);
  });
});

describe('nombres de estado', () => {
  it('nombra los estados de la solicitud de reserva con aprobación del anfitrión (SOF-79)', () => {
    expect(['PENDIENTE_APROBACION', 'RECHAZADA'].map(nombreDeEstadoReserva))
      .toEqual(['Pendiente de aprobación', 'Rechazada']);
  });

  it('nombra cada estado de la reserva y del pago para mostrarlo', () => {
    expect(['PENDIENTE', 'CONFIRMADA', 'CANCELADA', 'VENCIDA', 'COMPLETADA'].map(nombreDeEstadoReserva))
      .toEqual(['Pendiente de pago', 'Confirmada', 'Cancelada', 'Vencida', 'Completada']);
    expect(['SIN_PAGO', 'APROBADO', 'RECHAZADO', 'ERROR', 'REEMBOLSADO', 'NO_COBRADO'].map(nombreDeEstadoPago))
      .toEqual(['Sin pago', 'Aceptado', 'Rechazado', 'En verificación', 'Reembolsado', 'No cobrado']);
  });
});

describe('reservaDesdeApi', () => {
  it('convierte los importes, que llegan como texto, a número', () => {
    const reserva = reservaDesdeApi({
      id: 12, publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', estado: 'PENDIENTE', noches: 4,
      precioBase: '400000.00', tarifaLimpieza: '30000.00', tarifaServicio: '40000.00', descuento: '0.00',
      totalServiciosAdicionales: '15000.00', total: '485000.00', estadoPago: 'SIN_PAGO', transaccionId: null,
      venceEn: '2026-11-23T15:00:00Z',
    });

    expect(reserva).toEqual({
      id: 12, publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', estado: 'PENDIENTE', noches: 4,
      precioBase: 400000, tarifaLimpieza: 30000, tarifaServicio: 40000, descuento: 0,
      totalServiciosAdicionales: 15000, total: 485000, estadoPago: 'SIN_PAGO', transaccionId: null,
      venceEn: '2026-11-23T15:00:00Z',
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
