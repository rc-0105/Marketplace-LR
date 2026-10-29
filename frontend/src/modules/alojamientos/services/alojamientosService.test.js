import { beforeEach, describe, expect, it, vi } from 'vitest';
import { get, post, put } from '../../../shared/services/http.js';
import {
  actualizarImagenes, actualizarServicios, consultarPublicacion, crearPublicacion, editarPublicacion,
  listarMisPublicaciones, listarServicios, listarTiposAlojamiento,
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

  it('lista el catálogo de tipos de alojamiento', async () => {
    vi.mocked(get).mockResolvedValue([{ id: 1, nombre: 'Casa' }]);

    await expect(listarTiposAlojamiento()).resolves.toEqual([{ id: 1, nombre: 'Casa' }]);
    expect(get).toHaveBeenCalledWith('/api/v1/catalogos/tipos-alojamiento');
  });

  it('lista el catálogo de servicios', async () => {
    vi.mocked(get).mockResolvedValue([{ id: 1, nombre: 'Wifi' }]);

    await expect(listarServicios()).resolves.toEqual([{ id: 1, nombre: 'Wifi' }]);
    expect(get).toHaveBeenCalledWith('/api/v1/catalogos/servicios');
  });

  it('reemplaza por separado los servicios y las imágenes de la publicación', async () => {
    vi.mocked(put).mockResolvedValue({ id: 5, titulo: 'Casa', estado: 'BORRADOR' });

    await expect(actualizarServicios(5, [1, 2])).resolves.toMatchObject({ id: 5 });
    await expect(actualizarImagenes(5, ['https://imagenes.com/1.jpg'])).resolves.toMatchObject({ id: 5 });

    expect(put).toHaveBeenCalledWith('/api/v1/publicaciones/5/servicios', { servicios: [1, 2] });
    expect(put).toHaveBeenCalledWith('/api/v1/publicaciones/5/imagenes', { imagenes: ['https://imagenes.com/1.jpg'] });
  });
});
