import { beforeEach, describe, expect, it, vi } from 'vitest';
import { post } from '../../../shared/services/http.js';
import { crearResena } from './resenaService.js';

vi.mock('../../../shared/services/http.js', () => ({ post: vi.fn() }));

describe('crearResena', () => {
  beforeEach(() => vi.resetAllMocks());

  it('envía la calificación y el comentario a la reserva y devuelve la reseña convertida', async () => {
    vi.mocked(post).mockResolvedValue({
      id: 9, reservaId: 7, publicacionId: 5, calificacion: 4, contenido: 'Muy cómodo', fecha: '2026-12-20T15:00:00Z',
    });

    const resena = await crearResena(7, { calificacion: 4, contenido: 'Muy cómodo' });

    expect(post).toHaveBeenCalledWith('/api/v1/reservas/7/resena', { calificacion: 4, contenido: 'Muy cómodo' });
    expect(resena).toEqual({
      id: 9, reservaId: 7, publicacionId: 5, calificacion: 4, contenido: 'Muy cómodo', fecha: '2026-12-20T15:00:00Z',
    });
  });

  it('propaga el error de la API sin tragárselo', async () => {
    const error = Object.assign(new Error('Ya existe una reseña para esta reserva.'), { estado: 409 });
    vi.mocked(post).mockRejectedValue(error);

    await expect(crearResena(7, { calificacion: 5, contenido: 'Otra vez' })).rejects.toBe(error);
  });
});
