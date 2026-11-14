import { beforeEach, describe, expect, it, vi } from 'vitest';
import { get } from '../../../shared/services/http.js';
import { buscarAlojamientos, consultarDetalle } from './busquedaService.js';

vi.mock('../../../shared/services/http.js', () => ({ get: vi.fn() }));

describe('buscarAlojamientos', () => {
  beforeEach(() => vi.resetAllMocks());

  it('envía la ciudad y las fechas como parámetros codificados', async () => {
    vi.mocked(get).mockResolvedValue([]);

    await buscarAlojamientos({ ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25' });

    expect(get).toHaveBeenCalledWith('/api/v1/busqueda?ciudad=Santa+Marta&llegada=2026-12-20&salida=2026-12-25');
  });

  it('agrega los filtros informados y omite los vacíos', async () => {
    vi.mocked(get).mockResolvedValue([]);

    await buscarAlojamientos({
      ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25',
      capacidad: '4', tipo: 'Casa', precioMin: '', precioMax: '500000', servicios: [10, 20],
    });

    expect(get).toHaveBeenCalledWith(
      '/api/v1/busqueda?ciudad=Santa+Marta&llegada=2026-12-20&salida=2026-12-25'
      + '&capacidad=4&tipo=Casa&precioMax=500000&servicios=10&servicios=20');
  });

  it('consulta el detalle por id y convierte el precio a número', async () => {
    vi.mocked(get).mockResolvedValue({
      id: 5, titulo: 'Casa', precioNoche: '250000.00', servicios: ['Wifi'], periodos: [], calificacionPromedio: null,
    });

    const detalle = await consultarDetalle(5);

    expect(get).toHaveBeenCalledWith('/api/v1/busqueda/5');
    expect(detalle).toMatchObject({
      id: 5, precioNoche: 250000, servicios: ['Wifi'], imagenes: [], periodos: [], calificacionPromedio: null,
    });
  });

  it('convierte cada resultado al modelo del frontend', async () => {
    vi.mocked(get).mockResolvedValue([
      { id: 1, titulo: 'Casa', ciudad: 'Santa Marta', tipo: 'Casa', capacidad: 4, precioNoche: '250000.00' },
    ]);

    const resultados = await buscarAlojamientos({ ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25' });

    expect(resultados).toEqual([
      { id: 1, titulo: 'Casa', ciudad: 'Santa Marta', tipo: 'Casa', capacidad: 4, precioNoche: 250000, imagenPrincipal: null },
    ]);
  });
});
