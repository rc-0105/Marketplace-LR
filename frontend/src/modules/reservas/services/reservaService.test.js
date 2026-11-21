import { beforeEach, describe, expect, it, vi } from 'vitest';
import { post } from '../../../shared/services/http.js';
import { crearReserva, pagarReserva } from './reservaService.js';

vi.mock('../../../shared/services/http.js', () => ({ post: vi.fn() }));

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

    const reserva = await pagarReserva(7, 'pm_card_visa');

    expect(post).toHaveBeenCalledWith('/api/v1/reservas/7/pago', { metodoPago: 'pm_card_visa' });
    expect(reserva).toEqual(expect.objectContaining({
      id: 7, estado: 'CONFIRMADA', estadoPago: 'APROBADO', transaccionId: 'pi_123',
    }));
  });
});
