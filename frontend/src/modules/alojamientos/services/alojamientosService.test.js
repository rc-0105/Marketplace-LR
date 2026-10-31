import { beforeEach, describe, expect, it, vi } from 'vitest';
import { get, post, put } from '../../../shared/services/http.js';
import {
  activarPublicacion, actualizarConfiguracion, actualizarImagenes, actualizarServicios, consultarPublicacion,
  crearPublicacion, editarPublicacion, listarMisPublicaciones, listarPoliticasCancelacion, listarServicios,
  listarTiposAlojamiento, pausarPublicacion,
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

  it('activa y pausa la publicación y devuelve el modelo con el estado nuevo', async () => {
    vi.mocked(post)
      .mockResolvedValueOnce({ id: 5, titulo: 'Casa', estado: 'PENDIENTE_REVISION' })
      .mockResolvedValueOnce({ id: 5, titulo: 'Casa', estado: 'PAUSADA' });

    await expect(activarPublicacion(5)).resolves.toMatchObject({ id: 5, estado: 'PENDIENTE_REVISION' });
    await expect(pausarPublicacion(5)).resolves.toMatchObject({ id: 5, estado: 'PAUSADA' });

    expect(post).toHaveBeenCalledWith('/api/v1/publicaciones/5/activar');
    expect(post).toHaveBeenCalledWith('/api/v1/publicaciones/5/pausar');
  });

  it('lista el catálogo de políticas de cancelación', async () => {
    const politicas = [{ id: 1, nombre: 'Flexible', descripcion: 'Devolución total.', diasSinCosto: 1, porcentajeDevolucion: 50 }];
    vi.mocked(get).mockResolvedValue(politicas);

    await expect(listarPoliticasCancelacion()).resolves.toEqual(politicas);
    expect(get).toHaveBeenCalledWith('/api/v1/catalogos/politicas-cancelacion');
  });

  it('reemplaza la configuración de la publicación y la convierte al modelo con sus condiciones', async () => {
    const solicitud = { tarifaLimpieza: 40000, modoReserva: 'APROBACION', politicaCancelacion: 2, serviciosAdicionales: [] };
    vi.mocked(put).mockResolvedValue({
      id: 5, titulo: 'Casa', estado: 'BORRADOR', tarifaLimpieza: 40000, modoReserva: 'APROBACION',
      politicaCancelacion: { id: 2, nombre: 'Moderada' }, serviciosAdicionales: [{ id: 7, nombre: 'Desayuno', costo: 15000 }],
    });

    const publicacion = await actualizarConfiguracion(5, solicitud);

    expect(put).toHaveBeenCalledWith('/api/v1/publicaciones/5/configuracion', solicitud);
    expect(publicacion).toMatchObject({
      tarifaLimpieza: 40000,
      modoReserva: 'APROBACION',
      politicaCancelacion: { id: 2, nombre: 'Moderada' },
      serviciosAdicionales: [{ id: 7, nombre: 'Desayuno', costo: 15000 }],
    });
  });

  it('una publicación sin configurar queda con reserva inmediata y sin condiciones', async () => {
    vi.mocked(get).mockResolvedValue({ id: 5, titulo: 'Casa', estado: 'BORRADOR' });

    await expect(consultarPublicacion(5)).resolves.toMatchObject({
      tarifaLimpieza: null, modoReserva: 'INMEDIATA', politicaCancelacion: null, serviciosAdicionales: [],
    });
  });
});
