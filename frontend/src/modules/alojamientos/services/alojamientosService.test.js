import { beforeEach, describe, expect, it, vi } from 'vitest';
import { get, post, put } from '../../../shared/services/http.js';
import {
  consultarPublicacion, crearPublicacion, editarPublicacion, listarMisPublicaciones,
} from './alojamientosService.js';

vi.mock('../../../shared/services/http.js', async (importOriginal) => ({
  ...(await importOriginal()),
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}));

describe('alojamientosService', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lista las publicaciones del anfitrión y las convierte al modelo del frontend', async () => {
    vi.mocked(get).mockResolvedValue([
      { id: 3, titulo: 'Casa en la playa', tipo: 'CASA', estado: 'BORRADOR', camposFaltantes: ['reglas'] },
    ]);

    const publicaciones = await listarMisPublicaciones();

    expect(get).toHaveBeenCalledWith('/api/v1/publicaciones/mias');
    expect(publicaciones).toEqual([expect.objectContaining({
      id: 3,
      titulo: 'Casa en la playa',
      tipo: 'CASA',
      ciudad: null,
      servicios: [],
      imagenes: [],
      camposFaltantes: ['reglas'],
    })]);
  });

  it('consulta, crea y edita contra la ruta de publicaciones y devuelve el modelo', async () => {
    const respuesta = { id: 5, titulo: 'Casa', estado: 'BORRADOR' };
    vi.mocked(get).mockResolvedValue(respuesta);
    vi.mocked(post).mockResolvedValue(respuesta);
    vi.mocked(put).mockResolvedValue(respuesta);
    const solicitud = { titulo: 'Casa' };

    await expect(consultarPublicacion(5)).resolves.toMatchObject({ id: 5, servicios: [], imagenes: [] });
    await expect(crearPublicacion(solicitud)).resolves.toMatchObject({ id: 5 });
    await expect(editarPublicacion(5, solicitud)).resolves.toMatchObject({ id: 5 });

    expect(get).toHaveBeenCalledWith('/api/v1/publicaciones/5');
    expect(post).toHaveBeenCalledWith('/api/v1/publicaciones', solicitud);
    expect(put).toHaveBeenCalledWith('/api/v1/publicaciones/5', solicitud);
  });
});
