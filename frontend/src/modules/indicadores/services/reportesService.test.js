import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi, descargar, get } from '../../../shared/services/http.js';
import { consultarReporte, exportarReporte, guardarArchivo } from './reportesService.js';

vi.mock('../../../shared/services/http.js', async (importOriginal) => ({
  ...(await importOriginal()),
  get: vi.fn(),
  descargar: vi.fn(),
}));

describe('reportesService', () => {
  beforeEach(() => vi.resetAllMocks());

  afterEach(() => vi.unstubAllGlobals());

  it('consulta el reporte sin parámetros cuando no hay rango', async () => {
    vi.mocked(get).mockResolvedValue({ filas: [] });

    await expect(consultarReporte('reservas')).resolves.toEqual({ filas: [] });
    expect(get).toHaveBeenCalledWith('/api/v1/admin/reportes/reservas');
  });

  it('consulta el reporte enviando solo las fechas con valor', async () => {
    vi.mocked(get).mockResolvedValue({});

    await consultarReporte('usuarios', { desde: '2026-11-01', hasta: ' ' });

    expect(get).toHaveBeenCalledWith('/api/v1/admin/reportes/usuarios?desde=2026-11-01');
  });

  it('exporta el mismo reporte con el formato y las fechas', async () => {
    const archivo = { contenido: new Blob(['x']), nombre: 'reporte-reservas-2026-11-01-a-2026-11-30.xlsx' };
    vi.mocked(descargar).mockResolvedValue(archivo);

    await expect(exportarReporte('reservas', 'xlsx', { desde: '2026-11-01', hasta: '2026-11-30' }))
      .resolves.toBe(archivo);
    expect(descargar).toHaveBeenCalledWith(
      '/api/v1/admin/reportes/reservas?formato=xlsx&desde=2026-11-01&hasta=2026-11-30',
    );
  });

  it('propaga el error de la API al exportar', async () => {
    vi.mocked(descargar).mockRejectedValue(new ErrorApi(400, 'La fecha desde no puede ser posterior a la fecha hasta.'));

    await expect(exportarReporte('reservas', 'pdf')).rejects.toMatchObject({ estado: 400 });
  });

  it('guarda el archivo descargado con su nombre y libera la URL temporal', () => {
    const crear = vi.fn(() => 'blob:reporte');
    const liberar = vi.fn();
    vi.stubGlobal('URL', { ...URL, createObjectURL: crear, revokeObjectURL: liberar });
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const contenido = new Blob(['x']);

    guardarArchivo({ contenido, nombre: 'reporte-usuarios-historico.pdf' });

    expect(crear).toHaveBeenCalledWith(contenido);
    expect(clic).toHaveBeenCalledTimes(1);
    expect(clic.mock.contexts[0].download).toBe('reporte-usuarios-historico.pdf');
    expect(clic.mock.contexts[0].getAttribute('href')).toBe('blob:reporte');
    expect(liberar).toHaveBeenCalledWith('blob:reporte');
    clic.mockRestore();
  });
});
