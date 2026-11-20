import { beforeEach, describe, expect, it, vi } from 'vitest';
import { post } from '../../../shared/services/http.js';
import { crearReserva } from './reservaService.js';

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
    });
  });

  it('sin servicios adicionales envía una lista vacía', async () => {
    vi.mocked(post).mockResolvedValue({ id: 1, estado: 'PENDIENTE' });

    await crearReserva({ publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14' });

    expect(post).toHaveBeenCalledWith('/api/v1/reservas',
      expect.objectContaining({ serviciosAdicionalesIds: [] }));
  });

  it('devuelve la reserva convertida del modelo', async () => {
    vi.mocked(post).mockResolvedValue({ id: 1, estado: 'PENDIENTE', total: '470000.00' });

    const reserva = await crearReserva({ publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14' });

    expect(reserva).toEqual(expect.objectContaining({ id: 1, estado: 'PENDIENTE', total: 470000 }));
  });
});
