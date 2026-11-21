import { beforeEach, describe, expect, it, vi } from 'vitest';
import { get, post } from '../../../shared/services/http.js';
import { consultarResenas, crearResena } from './resenaService.js';

vi.mock('../../../shared/services/http.js', () => ({ get: vi.fn(), post: vi.fn() }));

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

describe('consultarResenas', () => {
  beforeEach(() => vi.resetAllMocks());

  it('pide las reseñas de la publicación y devuelve el promedio y la lista convertidos', async () => {
    vi.mocked(get).mockResolvedValue({
      promedio: 4.5,
      resenas: [
        { id: 9, reservaId: 7, autor: 'Ana Gómez', calificacion: 5, contenido: 'Perfecto', fecha: '2026-02-10T10:00:00Z' },
      ],
    });

    const resultado = await consultarResenas(5);

    expect(get).toHaveBeenCalledWith('/api/v1/publicaciones/5/resenas');
    expect(resultado).toEqual({
      promedio: 4.5,
      resenas: [
        { id: 9, reservaId: 7, autor: 'Ana Gómez', calificacion: 5, contenido: 'Perfecto', fecha: '2026-02-10T10:00:00Z' },
      ],
    });
  });

  it('sin reseñas deja el promedio en null y la lista vacía', async () => {
    vi.mocked(get).mockResolvedValue({ promedio: null, resenas: [] });

    expect(await consultarResenas(5)).toEqual({ promedio: null, resenas: [] });
  });

  it('propaga el error de la API sin tragárselo', async () => {
    const error = Object.assign(new Error('Sin permiso'), { estado: 403 });
    vi.mocked(get).mockRejectedValue(error);

    await expect(consultarResenas(5)).rejects.toBe(error);
  });
});
