import { beforeEach, describe, expect, it, vi } from 'vitest';
import { get, post } from '../../../shared/services/http.js';
import {
  cancelarReserva, consultarReserva, crearReserva, listarMisReservas, pagarReserva,
} from './reservaService.js';

vi.mock('../../../shared/services/http.js', () => ({ get: vi.fn(), post: vi.fn() }));

describe('listarMisReservas', () => {
  beforeEach(() => vi.resetAllMocks());

  it('pide mis reservas y devuelve las futuras y las pasadas convertidas', async () => {
    vi.mocked(get).mockResolvedValue({
      futuras: [{ id: 2, estado: 'CONFIRMADA', total: '220000.00' }],
      pasadas: [{ id: 1, estado: 'CANCELADA', total: '200000.00' }],
    });

    const mias = await listarMisReservas();

    expect(get).toHaveBeenCalledWith('/api/v1/reservas/mias');
    expect(mias.futuras).toEqual([expect.objectContaining({ id: 2, estado: 'CONFIRMADA', total: 220000 })]);
    expect(mias.pasadas).toEqual([expect.objectContaining({ id: 1, estado: 'CANCELADA', total: 200000 })]);
  });
});

describe('consultarReserva', () => {
  beforeEach(() => vi.resetAllMocks());

  it('pide la reserva por su id y devuelve el detalle convertido', async () => {
    vi.mocked(get).mockResolvedValue({ id: 7, estado: 'CONFIRMADA', total: '470000.00', serviciosAdicionales: [] });

    const detalle = await consultarReserva(7);

    expect(get).toHaveBeenCalledWith('/api/v1/reservas/7');
    expect(detalle).toEqual(expect.objectContaining({ id: 7, estado: 'CONFIRMADA', total: 470000 }));
  });
});

describe('crearReserva', () => {
  beforeEach(() => vi.resetAllMocks());

  it('envía la publicación, las fechas y los servicios adicionales elegidos', async () => {
    vi.mocked(post).mockResolvedValue({ id: 1, estado: 'PENDIENTE' });

    await crearReserva({
      publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', serviciosAdicionalesIds: [1],
    });

    expect(post).toHaveBeenCalledWith('/api/v1/reservas', {
      publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', serviciosAdicionalesIds: [1],
    }, expect.anything());
  });

  it('sin servicios adicionales envía una lista vacía', async () => {
    vi.mocked(post).mockResolvedValue({ id: 1, estado: 'PENDIENTE' });

    await crearReserva({ publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14' });

    expect(post).toHaveBeenCalledWith('/api/v1/reservas',
      expect.objectContaining({ serviciosAdicionalesIds: [] }), expect.anything());
  });

  it('envía la clave de idempotencia recibida en la cabecera Idempotency-Key (SOF-77)', async () => {
    vi.mocked(post).mockResolvedValue({ id: 1, estado: 'PENDIENTE' });

    await crearReserva({ publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14' },
      '0b6a1c2e-3f4d-4a5b-8c6d-7e8f9a0b1c2d');

    expect(post).toHaveBeenCalledWith('/api/v1/reservas', expect.anything(),
      { 'Idempotency-Key': '0b6a1c2e-3f4d-4a5b-8c6d-7e8f9a0b1c2d' });
  });

  it('sin clave genera un UUID distinto en cada intento', async () => {
    vi.mocked(post).mockResolvedValue({ id: 1, estado: 'PENDIENTE' });
    const datos = { publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14' };

    await crearReserva(datos);
    await crearReserva(datos);

    const [primera, segunda] = vi.mocked(post).mock.calls.map(([, , cabeceras]) => cabeceras['Idempotency-Key']);
    expect(primera).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
    expect(segunda).not.toBe(primera);
  });

  it('devuelve la reserva convertida del modelo', async () => {
    vi.mocked(post).mockResolvedValue({ id: 1, estado: 'PENDIENTE', total: '470000.00' });

    const reserva = await crearReserva({ publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14' });

    expect(reserva).toEqual(expect.objectContaining({ id: 1, estado: 'PENDIENTE', total: 470000 }));
  });
});

describe('pagarReserva', () => {
  beforeEach(() => vi.resetAllMocks());

  it('envía el método de pago de prueba a la ruta de pago de la reserva y devuelve la reserva convertida', async () => {
    vi.mocked(post).mockResolvedValue({
      id: 7, estado: 'CONFIRMADA', total: '470000.00', estadoPago: 'APROBADO', transaccionId: 'pi_123',
    });

    const clave = '0b6a1c2e-3f4d-4a5b-8c6d-7e8f9a0b1c2d';
    const reserva = await pagarReserva(7, 'pm_card_visa', clave);

    expect(post).toHaveBeenCalledWith('/api/v1/reservas/7/pago', { metodoPago: 'pm_card_visa' },
      { 'Idempotency-Key': clave });
    expect(reserva).toEqual(expect.objectContaining({
      id: 7, estado: 'CONFIRMADA', estadoPago: 'APROBADO', transaccionId: 'pi_123',
    }));
  });

  it('sin clave envía un UUID nuevo en cada llamada', async () => {
    vi.mocked(post).mockResolvedValue({ id: 7 });
    await pagarReserva(7, 'pm_card_visa');
    await pagarReserva(7, 'pm_card_visa');

    const claves = vi.mocked(post).mock.calls.map(([, , cabeceras]) => cabeceras?.['Idempotency-Key']);
    const uuid = expect.stringMatching(/^[\da-f]{8}-[\da-f]{4}-4[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/);
    expect(claves).toEqual([uuid, uuid]);
    expect(claves[0]).not.toBe(claves[1]);
  });
});

describe('cancelarReserva', () => {
  beforeEach(() => vi.resetAllMocks());

  it('envía el motivo a la ruta de cancelación de la reserva y devuelve la cancelación convertida', async () => {
    vi.mocked(post).mockResolvedValue({
      reservaId: 7, estado: 'CANCELADA', motivo: 'Cambio de planes', valorDevolucion: '470000.00',
      fecha: '2026-12-01T15:00:00Z',
    });

    const cancelacion = await cancelarReserva(7, 'Cambio de planes');

    expect(post).toHaveBeenCalledWith('/api/v1/reservas/7/cancelacion', { motivo: 'Cambio de planes' });
    expect(cancelacion).toEqual({
      reservaId: 7, estado: 'CANCELADA', motivo: 'Cambio de planes', valorDevolucion: 470000,
      fecha: '2026-12-01T15:00:00Z',
    });
  });
});
