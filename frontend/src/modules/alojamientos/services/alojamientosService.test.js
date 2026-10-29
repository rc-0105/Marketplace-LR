import { beforeEach, describe, expect, it, vi } from 'vitest';
import { get } from '../../../shared/services/http.js';
import { listarMisPublicaciones } from './alojamientosService.js';

vi.mock('../../../shared/services/http.js', async (importOriginal) => ({
  ...(await importOriginal()),
  get: vi.fn(),
}));

describe('alojamientosService', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lista las publicaciones del anfitrión y las convierte al modelo del frontend', async () => {
    vi.mocked(get).mockResolvedValue([
      { id: 3, titulo: 'Casa en la playa', tipo: { id: 2, nombre: 'Casa' }, estado: 'BORRADOR', camposFaltantes: ['reglas'] },
    ]);

    const publicaciones = await listarMisPublicaciones();

    expect(get).toHaveBeenCalledWith('/api/v1/publicaciones/mias');
    expect(publicaciones).toEqual([expect.objectContaining({
      id: 3,
      titulo: 'Casa en la playa',
      tipo: { id: 2, nombre: 'Casa' },
      ciudad: null,
      servicios: [],
      imagenes: [],
      camposFaltantes: ['reglas'],
    })]);
  });
});
